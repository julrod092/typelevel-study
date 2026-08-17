import * as path from "node:path";
import * as cdk from "aws-cdk-lib";
import * as dynamodb from "aws-cdk-lib/aws-dynamodb";
import * as kinesis from "aws-cdk-lib/aws-kinesis";
import * as lambda from "aws-cdk-lib/aws-lambda";
import * as lambdaEventSources from "aws-cdk-lib/aws-lambda-event-sources";
import * as s3 from "aws-cdk-lib/aws-s3";
import { Construct } from "constructs";
import { DynamoDBClient, PutItemCommand } from "@aws-sdk/client-dynamodb";
import type { AttributeValue } from "@aws-sdk/client-dynamodb";

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

const ordersTableName = process.env.ORDERS_TABLE_NAME || 'Orders';
const customersTableName = process.env.CUSTOMERS_TABLE_NAME || 'Customers';
const couponsTableName = process.env.COUPONS_TABLE_NAME || 'Coupons';

class TypeLevelLocalStack extends cdk.Stack {

    constructor(scope: Construct, id: string, props: cdk.StackProps) {
        super(scope, id, props);

        const customersTable = new dynamodb.Table(this, "CustomersTable", {
            tableName: customersTableName,
            partitionKey: {
                name: "customerId",
                type: dynamodb.AttributeType.STRING
            },
            billingMode: dynamodb.BillingMode.PAY_PER_REQUEST,
            removalPolicy: cdk.RemovalPolicy.DESTROY
        });

        const couponsTable = new dynamodb.Table(this, "CouponsTable", {
            tableName: couponsTableName,
            partitionKey: {
                name: "couponCode",
                type: dynamodb.AttributeType.STRING
            },
            billingMode: dynamodb.BillingMode.PAY_PER_REQUEST,
            removalPolicy: cdk.RemovalPolicy.DESTROY
        });

        const ordersTable = new dynamodb.Table(this, "OrdersTable", {
            tableName: ordersTableName,
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

// Seed definition

const customersSeedData: Record<string, AttributeValue>[] = [
    {
        customerId: { S: "customer-1" },
        tier: { S: "BASIC" },
        name: { S: "John Doe" },
        createdAt: { S: "2026-08-16T00:00:00Z"}
    },{
        customerId: { S: "customer-2" },
        tier: { S: "SILVER" },
        name: { S: "Alan Doe" },
        createdAt: { S: "2026-08-16T00:00:00Z"}
    },{
        customerId: { S: "customer-3" },
        tier: { S: "GOLD" },
        name: { S: "Willian Dafoe" },
        createdAt: { S: "2026-08-16T00:00:00Z"}
    }
];

const couponsSeedData: Record<string, AttributeValue>[] = [
    {
        couponCode: { S: "SUMMER10" },
        discountPercent: { N: "10" },
        minOrderAmount: { N: "150" },
        usageCount: { N: "0" },
        usageLimit: { N: "2" },
        expiresAt: { S: "2027-08-16T00:00:00Z" },
        stackableWithTier: { BOOL: true }
    },{
        couponCode: { S: "ALL15" },
        discountPercent: { N: "15" },
        minOrderAmount: { N: "200" },
        usageCount: { N: "0" },
        usageLimit: { N: "10" },
        expiresAt: { S: "2027-08-16T00:00:00Z" },
        stackableWithTier: { BOOL: true }
    },{
        couponCode: { S: "EXPIRED5" },
        discountPercent: { N: "5" },
        minOrderAmount: { N: "10" },
        usageCount: { N: "0" },
        usageLimit: { N: "10" },
        expiresAt: { S: "2025-08-16T00:00:00Z" },
        stackableWithTier: { BOOL: true }
    }
];

async function seedLocalData(): Promise<void> {

    const client = new DynamoDBClient({
        region: process.env.AWS_DEFAULT_REGION,
        endpoint: process.env.AWS_ENDPOINT_URL,
        credentials: {
            accessKeyId: process.env.AWS_ACCESS_KEY_ID ?? "test",
            secretAccessKey: process.env.AWS_SECRET_ACCESS_KEY ?? "test"
        }
    });

    await Promise.all(
        customersSeedData.map( (item) =>
            client.send(
                new PutItemCommand({
                    TableName: customersTableName,
                    Item: item
                })
            )
        )
    );

    await Promise.all(
        couponsSeedData.map( (item) =>
            client.send(
                new PutItemCommand({
                    TableName: couponsTableName,
                    Item: item
                })
            )
                           )
    );

    client.destroy();

    console.log(`Seeded ${customersSeedData.length} customers and ${couponsSeedData.length} coupons`)
};


async function main(): Promise<void> {
    if (process.argv.includes("--seed")) {
        await seedLocalData();
        return;
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

};


void main().catch( (error: unknown) => {
    console.error(error);
    process.exitCode = 1;
});
