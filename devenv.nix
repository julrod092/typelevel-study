{
  pkgs,
  lib,
  config,
  ...
}: let
  compose = "docker compose -f deployment/docker-compose.yml";
  awsLocal = ''aws --endpoint-url "$AWS_ENDPOINT_URL" --region "$AWS_DEFAULT_REGION"'';
  checkLocalRegion = ''
    if [ "$AWS_DEFAULT_REGION" != "us-east-1" ]; then
      printf '%s\n' "The local CDK stack requires AWS_DEFAULT_REGION=us-east-1." >&2
      exit 1
    fi
  '';
  deployLambda = ''
    set -euo pipefail
    ${checkLocalRegion}
    ${compose} up -d --wait localstack
    npm --prefix deployment ci
    sbt "eventHandler/assembly"

    key="stream-processor-$(date -u +%Y%m%dT%H%M%SZ)-$$.jar"
    if ! ${awsLocal} s3api head-bucket --bucket "$LAMBDA_CODE_BUCKET" 2>/dev/null; then
      ${awsLocal} s3 mb "s3://$LAMBDA_CODE_BUCKET"
    fi
    ${awsLocal} s3 cp "$LAMBDA_JAR" "s3://$LAMBDA_CODE_BUCKET/$key"
    npm --prefix deployment run deploy:local -- \
      --parameters "LambdaCodeBucket=$LAMBDA_CODE_BUCKET" \
      --parameters "LambdaCodeKey=$key" \
      --parameters "LambdaEndpointUrl=$LAMBDA_ENDPOINT_URL"
  '';
in {
  languages = {
    java = {
      enable = true;
      jdk.package = pkgs.zulu21;
    };
    javascript = {
      enable = true;
      package = pkgs.nodejs_latest;
    };
  };

  packages = with pkgs; [
    (sbt.override {jre = pkgs.zulu21;})
    scala-cli
    metals
    scalafmt
    awscli2
    curl
    docker-client
    docker-compose
  ];

  env = {
    AWS_ACCESS_KEY_ID = lib.mkDefault "test";
    AWS_SECRET_ACCESS_KEY = lib.mkDefault "test";
    AWS_DEFAULT_REGION = lib.mkDefault "us-east-1";
    AWS_ENDPOINT_URL = lib.mkDefault "http://localhost:4566";
    AWS_ENDPOINT_URL_S3 = lib.mkDefault "http://s3.localhost.localstack.cloud:4566";
    AWS_PAGER = lib.mkDefault "";
    LAMBDA_CODE_BUCKET = lib.mkDefault "typelevel-lambda-artifacts";
    LAMBDA_JAR = lib.mkDefault "event-handler/target/scala-3.8.4/stream-processor.jar";
    LAMBDA_ENDPOINT_URL = lib.mkDefault "http://localhost.localstack.cloud:4566";
    KINESIS_STREAM_NAME = lib.mkDefault "OrderPricedStream";
    SERVICE_HOST = lib.mkDefault "0.0.0.0";
    SERVICE_PORT = lib.mkDefault "8081";
    ORDERS_TABLE_NAME = lib.mkDefault "Orders";
    CUSTOMERS_TABLE_NAME = lib.mkDefault "Customers";
    COUPONS_TABLE_NAME = lib.mkDefault "Coupons";
    TESTCONTAINERS_RYUK_DISABLED = if (pkgs.stdenv.hostPlatform.isLinux) then true else false;
  };

  enterShell = ''
    ${
      if pkgs.stdenv.hostPlatform.isDarwin
      then ''
        export DOCKER_HOST="''${DOCKER_HOST:-unix://$HOME/.colima/default/docker.sock}"
      ''
      else ''
        export DOCKER_SOCK="''${DOCKER_SOCK:-''${XDG_RUNTIME_DIR:-/run/user/$UID}/podman/podman.sock}"
        export DOCKER_HOST="''${DOCKER_HOST:-unix://$DOCKER_SOCK}"
      ''
    }
    export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE="''${TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE:-/var/run/docker.sock}"
  '';

  scripts = {
    deployment-install.exec = "npm --prefix deployment ci";
    deployment-typecheck.exec = "npm --prefix deployment run typecheck";
    deployment-synth.exec = "npm --prefix deployment run synth";
    deployment-deploy-local.exec = deployLambda;
    deployment-destroy-local.exec = "npm --prefix deployment run destroy:local";
    lambda-build.exec = ''sbt "eventHandler/assembly"'';
  };

  processes = lib.optionalAttrs (!config.devenv.isTesting) {
    localstack = {
      exec = ''
        ${checkLocalRegion}
        ${compose} up localstack
      '';
      ready = {
        http.get = {
          port = 4566;
          path = "/_localstack/health";
        };
        initial_delay = 2;
        period = 2;
        timeout = 60;
      };
    };
    lambda-deploy = {
      exec = deployLambda;
      after = [ "devenv:processes:localstack@ready" ];
      restart.on = "never";
    };

    dynamo-setup = {
      exec = ''
        ${checkLocalRegion}
        npm --prefix deployment run seed:local
      '';
      after = [ "devenv:processes:lambda-deploy@completed" ];
      restart.on = "never";
    };

    typelevel = {
      exec = ''
        ${checkLocalRegion}
        sbt "api/runMain com.example.Main"
      '';
      after = [ "devenv:processes:dynamo-setup@completed" ];
    };
  };

  enterTest = ''
    sbt "core/test" "api/test" "eventHandler/test" "it/test"
  '';
}
