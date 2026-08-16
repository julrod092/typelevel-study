{
  pkgs,
  lib,
  ...
}: let
  aws-cdk-local = pkgs.callPackage ./packages/aws-cdk-local {
    nodejs_24 = pkgs.nodejs_24;
  };
  compose = "docker compose -f deployment/docker-compose.yml";
in {
  languages.java = {
    enable = true;
    jdk.package = pkgs.zulu21;
  };

  packages = with pkgs; [
    (sbt.override {jre = pkgs.zulu21;})
    scala-cli
    metals
    scalafmt
    nodejs_24
    aws-cdk-local
    awscli2
    docker-client
    docker-compose
  ];

  env = {
    AWS_ACCESS_KEY_ID = lib.mkDefault "test";
    AWS_SECRET_ACCESS_KEY = lib.mkDefault "test";
    AWS_DEFAULT_REGION = lib.mkDefault "us-east-1";
    AWS_ENDPOINT_URL = lib.mkDefault "http://localhost:4566";
    AWS_ENDPOINT_URL_S3 = lib.mkDefault "http://s3.localhost.localstack.cloud:4566";
  };

  enterShell = ''
    export DOCKER_SOCK="''${DOCKER_SOCK:-''${XDG_RUNTIME_DIR:-/run/user/$UID}/podman/podman.sock}"
    export DOCKER_HOST="''${DOCKER_HOST:-unix://$DOCKER_SOCK}"
    export NODE_PATH="$DEVENV_ROOT/deployment/node_modules''${NODE_PATH:+:$NODE_PATH}"
  '';

  scripts = {
    deployment-install.exec = "npm --prefix deployment ci";
    deployment-typecheck.exec = "npm --prefix deployment run typecheck";
    deployment-synth.exec = "npm --prefix deployment run synth";
    deployment-deploy-local.exec = "npm --prefix deployment run deploy:local";
    deployment-destroy-local.exec = "npm --prefix deployment run destroy:local";
  };

  processes.localstack = {
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

  enterTest = ''
    java -version
    sbt --script-version
    node --version
    npm --version
    cdklocal --version
  '';
}
