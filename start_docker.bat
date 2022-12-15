docker build -t amazon_zio_scala_vm .
docker run --name amazon_zio_lambda_compile --mount src="$(pwd)",target=/root/project_mount,type=bind -t -d amazon_zio_scala_vm
docker exec -it amazon_zio_lambda_compile bash
