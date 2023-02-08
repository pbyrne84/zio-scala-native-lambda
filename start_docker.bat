docker build -t amazon_zio_scala_vm .
rem pwd seems to break on background windows updates with relative issues, hopefully this enables a stable commit  :(
set "currentDir=%cd%"
echo %currentDir%
docker run --name amazon_zio_lambda_compile --mount src="%currentDir%",target=/root/project_mount,type=bind -t -d amazon_zio_scala_vm
docker exec -it amazon_zio_lambda_compile bash
