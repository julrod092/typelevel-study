import * as path from "node:path";
import * as cdk from "aws-cdk-lib";
import * as dynamodb from "aws-cdk-lib/aws-dynamodb";
import * as kinesis from "aws-cdk-lib/aws-kinesis";
import * as lambda from "aws-cdk-lib/aws-lambda";
import * as lambdaEventSources from "aws-cdk-lib/aws-lambda-event-sources";
import * as s3 from "aws-cdk-lib/aws-s3";
import { Construct } from "constructs";

function requiredEnvironment(name: string): string {
    const value = process.env[name];

    if (!value) {
        throw new Error(`${name} must be set`);
    }

    return value;
}

//const lambdaMountDirectory = requiredEnvironment("LAMBDA_MOUNT_CWD");
//const lambdaHandler = requiredEnvironment("LAMBDA_HANDLER");

//if (!path.isAbsolute(lambdaMountDirectory)) {
//    throw new Error("LAMBDA_MOUNT_CWD must be an absolute path");
//}

class TypeLevelLocalStack extends cdk.Stack {

    constructor(scope: Construct, id: string, props: cdk.StackProps) {
        super(scope, id, props);

        const customersTable = new dynamodb.Table(this, "CustomersTable", {
            tableName: "Customers",
            partitionKey: {
                name: "customerId",
                type: dynamodb.AttributeType.STRING
            },
            billingMode: dynamodb.BillingMode.PAY_PER_REQUEST,
            removalPolicy: cdk.RemovalPolicy.DESTROY
        });

        const couponsTable = new dynamodb.Table(this, "CouponsTable", {
            tableName: "Coupons",
            partitionKey: {
                name: "couponCode",
                type: dynamodb.AttributeType.STRING
            },
            billingMode: dynamodb.BillingMode.PAY_PER_REQUEST,
            removalPolicy: cdk.RemovalPolicy.DESTROY
        });

        const ordersTable = new dynamodb.Table(this, "OrdersTable", {
            tableName: "Orders",
            partitionKey: {
                name: "orderId",
                type: dynamodb.AttributeType.STRING
            },
            billingMode: dynamodb.BillingMode.PAY_PER_REQUEST,
            stream: dynamodb.StreamViewType.NEW_IMAGE,
            removalPolicy: cdk.RemovalPolicy.DESTROY
        });

        new cdk.CfnOutput(this, "CustomersTableName", {
            value: customersTable.tableName
        });

        new cdk.CfnOutput(this, "CouponsTableName", {
            value: couponsTable.tableName
        });

        new cdk.CfnOutput(this, "OrdersTableName", {
            value: ordersTable.tableName
        });

        new cdk.CfnOutput(this, "OrdersStreamArn", {
            value: ordersTable.tableStreamArn!
        });
    }
}

const app = new cdk.App();

new TypeLevelLocalStack(app, "TypeLevelLocalStack", {
    env: {
        account: "000000000000",
        region: "us-east-1"
    },
    synthesizer: new cdk.BootstraplessSynthesizer()
});

app.synth();
