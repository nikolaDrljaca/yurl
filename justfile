# Start(up) either the local or prodlike docker stack
[group('dev')]
[arg('env', pattern='local|prodlike', help='Start the {local} or {prodlike} docker stack')]
up env:
    @echo 'Starting {{env}} stack.'
    docker compose -f docker-compose.{{env}}.yaml up --build -d

# Stop(down) either the local or prodlike docker stack
[group('dev')]
[arg('env', pattern='local|prodlike', help='Shut down the {local} or {prodlike} docker stack')]
down env:
    @echo 'Stopping {{env}} stack.'
    docker compose -f docker-compose.{{env}}.yaml down
