# cf-HelloSpring
A Hello World Cloud Foundry Example written with Spring Boot


## Requirements
* Cloud Foundry CLI: https://github.com/cloudfoundry/cli/releases
* GIT: https://git-scm.com/downloads

## Instructions
* Clone this repo: `git clone https://github.com/vchrisb/cf-HelloSpring.git`
* Open a shell and change into the `cf-HelloSpring` folder
* Login to Cloud Foundry: `cf login`
* Modify the application name in `manifest.yml` to be unique
* Build the application `./mvnw clean package`
* push the application to Cloud Foundry with: `cf push --strategy rolling -p target/hellospring-0.0.1-SNAPSHOT.jar`

## Inject failure

Access `https://<app url>/fail/ready` to fail readiness for a random instance for one minute, or for a specific app:

```
curl https://<app url>/fail/ready -X POST -H "X-Cf-App-Instance":"APP-GUID:INSTANCE-INDEX-NUMBER"
```

Access `https://<app url>/fail/live` to fail liveness or for a specific app:

```
curl https://<app url>/fail/live -X POST -H "X-Cf-App-Instance":"APP-GUID:INSTANCE-INDEX-NUMBER"
```

## Kill

Access `https://<app url>/kll` to kill a random instance, or kill a specific app:

```
curl https://<app url>/kill -X POST -H"X-Cf-App-Instance":"APP-GUID:INSTANCE-INDEX-NUMBER"
```
