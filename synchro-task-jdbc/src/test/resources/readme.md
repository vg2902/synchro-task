For some lock providers, the tests are implemented with the help of [TestContainers](https://www.testcontainers.org).
They require [Docker](https://www.docker.com) infrastructure to be available on your machine. 

These tests are not triggered by default and can be enabled by providing `docker-tests` Maven profile:

```shell
mvn clean test -P docker-tests
```

In order to run all tests, pass both profiles:
```shell
mvn clean test -P docker-tests,common-tests
```