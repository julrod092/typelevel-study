FOLDER := deployment
FILE := docker-compose.yml
NAME := typelevel-project
SBT := sbt

.PHONY: up deploy test-integration down

up:
	docker compose --project-name $(NAME) -f $(FOLDER)/$(FILE) up -d --wait localstack
	sbt "api/runMain com.example.Main"

deploy:
	npm --prefix $(FOLDER) ci
	npm --prefix $(FOLDER) run deploy:local
	npm --prefix $(FOLDER) run seed:local

test-integration:
	sbt "test"
	sbt "apiIntegration/test"

down:
	docker compose --project-name $(NAME) -f $(FOLDER)/$(FILE) down --remove-orphans
