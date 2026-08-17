{
  pkgs,
  lib,
  ...
}: let
  compose = "docker compose -f deployment/docker-compose.yml";
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
    SERVICE_HOST = lib.mkDefault "0.0.0.0";
    SERVICE_PORT = lib.mkDefault "8080";
    ORDERS_TABLE_NAME = lib.mkDefault "Orders";
    CUSTOMERS_TABLE_NAME = lib.mkDefault "Customers";
    COUPONS_TABLE_NAME = lib.mkDefault "Coupons";
  };

  enterShell = ''
    export DOCKER_SOCK="''${DOCKER_SOCK:-''${XDG_RUNTIME_DIR:-/run/user/$UID}/podman/podman.sock}"
    export DOCKER_HOST="''${DOCKER_HOST:-unix://$DOCKER_SOCK}"
  '';

  scripts = {
    deployment-install.exec = "npm --prefix deployment ci";
    deployment-typecheck.exec = "npm --prefix deployment run typecheck";
    deployment-synth.exec = "npm --prefix deployment run synth";
    deployment-deploy-local.exec = "npm --prefix deployment run deploy:local";
    deployment-destroy-local.exec = "npm --prefix deployment run destroy:local";
  };

  processes = {
    localstack = {
      exec = "${compose} up localstack";
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
    typelevel = {
      exec = ''sbt "api/runMain com.example.Main"'';
      after = [ "devenv:processes:localstack" ];
    };
  };

  enterTest = ''
    java -version
    sbt --script-version
    node --version
    npm --version
    deployment/node_modules/.bin/cdklocal --version
  '';
}
