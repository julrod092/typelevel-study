FOLDER := deployment
FILE := docker-compose.yml

export COMPOSE_PROJECT_NAME ?= typelevel-project
export AWS_ACCESS_KEY_ID ?= test
export AWS_SECRET_ACCESS_KEY ?= test
export AWS_DEFAULT_REGION ?= us-east-1
export AWS_ENDPOINT_URL ?= http://localhost:4566
export AWS_ENDPOINT_URL_S3 ?= http://s3.localhost.localstack.cloud:4566
export AWS_PAGER ?=
export KINESIS_STREAM_NAME ?= OrderPricedStream
export SERVICE_HOST ?= 0.0.0.0
export SERVICE_PORT ?= 8081
export ORDERS_TABLE_NAME ?= Orders
export CUSTOMERS_TABLE_NAME ?= Customers
export COUPONS_TABLE_NAME ?= Coupons

LAMBDA_CODE_BUCKET ?= typelevel-lambda-artifacts
LAMBDA_JAR ?= event-handler/target/scala-3.8.4/stream-processor.jar
LAMBDA_ENDPOINT_URL ?= http://localhost.localstack.cloud:4566

COMPOSE = docker compose -f "$(FOLDER)/$(FILE)"
AWS_LOCAL = aws --endpoint-url "$(AWS_ENDPOINT_URL)" --region "$(AWS_DEFAULT_REGION)"

.PHONY: up localstack api deploy lambda-build lambda-deploy seed \
	deployment-install deployment-typecheck deployment-synth deployment-destroy-local \
	check-local-config test-integration down

up: deploy
	sbt "api/runMain com.example.Main"

check-local-config:
	@if [ "$(AWS_DEFAULT_REGION)" != "us-east-1" ]; then \
		printf '%s\n' "The local CDK stack requires AWS_DEFAULT_REGION=us-east-1." >&2; \
		exit 1; \
	fi

localstack: check-local-config
	$(COMPOSE) up -d --wait localstack

api: check-local-config
	sbt "api/runMain com.example.Main"

deployment-install:
	npm --prefix "$(FOLDER)" ci

lambda-build:
	sbt "eventHandler/assembly"

# Keep the upload and deployment in one shell so they use the same unique object key.
lambda-deploy: localstack deployment-install lambda-build
	@set -eu; \
	key="stream-processor-$$(date -u +%Y%m%dT%H%M%SZ)-$$$$.jar"; \
	if ! $(AWS_LOCAL) s3api head-bucket --bucket "$(LAMBDA_CODE_BUCKET)" 2>/dev/null; then \
		$(AWS_LOCAL) s3 mb "s3://$(LAMBDA_CODE_BUCKET)"; \
	fi; \
	$(AWS_LOCAL) s3 cp "$(LAMBDA_JAR)" "s3://$(LAMBDA_CODE_BUCKET)/$$key"; \
	npm --prefix "$(FOLDER)" run deploy:local -- \
		--parameters "LambdaCodeBucket=$(LAMBDA_CODE_BUCKET)" \
		--parameters "LambdaCodeKey=$$key" \
		--parameters "LambdaEndpointUrl=$(LAMBDA_ENDPOINT_URL)"

deploy: lambda-deploy
	npm --prefix "$(FOLDER)" run seed:local

seed: check-local-config
	npm --prefix "$(FOLDER)" run seed:local

deployment-typecheck:
	npm --prefix "$(FOLDER)" run typecheck

deployment-synth:
	npm --prefix "$(FOLDER)" run synth

deployment-destroy-local:
	npm --prefix "$(FOLDER)" run destroy:local

test-integration:
	sbt "it/test"

down:
	$(COMPOSE) down --remove-orphans
