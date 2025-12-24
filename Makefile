prodlike-up:
	docker compose -f docker-compose.prodlike.yaml up --build -d

prodlike-down:
	docker compose -f docker-compose.prodlike.yaml down

local-up:
	docker compose -f docker-compose.local.yaml up --build -d

local-down:
	docker compose -f docker-compose.local.yaml down
