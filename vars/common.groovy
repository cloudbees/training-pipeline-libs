def runPreDeploymentTests(serviceName, registry) {
    def dir = pwd()
    sh "mkdir -p ${dir}/db"
    sh "chmod 0777 ${dir}/db"
      sh "docker run --rm -v $dir/target/scala-2.10:/source/target/scala-2.10 -v db:/data/db ${registry}/${serviceName}-tests ./run_tests.sh"
    }

def build(serviceName, registry) {
      sh "docker build -t ${registry}/books-ms ."
      sh "docker push ${registry}/books-ms"
      stash includes: "docker-compose*.yml", name: "docker-compose"

}

def deploy(serviceName, registry) {
    node("production") {
        def response = input message: 'Please confirm deployment to production', ok: 'Submit', parameters: [[$class: 'StringParameterDefinition', defaultValue: '', description: 'Additional comments', name: '']], submitter: 'manager'
        echo response
        unstash "docker-compose"
        def pull = [:]
        pull["service"] = {
            docker.image("${registry}/${serviceName}").pull()
        }
        pull["db"] = {
            docker.image("mongo").pull()
        }
        parallel pull
        sh "docker-compose -p books-ms up -d app"
        sleep 2
    }
}

def runPostDeploymentTests(serviceName, registry, domain) {
    def tests = docker.image("${registry}/${serviceName}-tests")
    tests.inside() {
        withEnv(["TEST_TYPE=integ", "DOMAIN=http://172.17.0.1:8081"]) {
            retry(2) {
                sh "./run_tests.sh"
                    }
        }
        }
}

return this;
