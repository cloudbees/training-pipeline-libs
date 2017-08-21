def runPreDeploymentTests(serviceName, registry) {
    def dir = pwd()
    sh "mkdir -p ${dir}/db"
    sh "chmod 0777 ${dir}/db"
    sh "docker run --rm -v $dir/target/scala-2.10:/source/target/scala-2.10 -v db:/data/db ${registry}/${serviceName}-tests ./run_tests.sh"
    sh "docker run -v ${dir}:/workspace ${registry}/${serviceName}-tests chmod 0777 /workspace"
    }

def build(serviceName, registry) {
      sh "docker build -t ${registry}/${serviceName} ."
      sh "docker push ${registry}/${serviceName}"
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

def runPostDeploymentTests(serviceName, registry) {
    def tests = docker.image("${registry}/${serviceName}-tests")
    tests.inside('-u 0:0') {
        withEnv(["TEST_TYPE=integ", "DOMAIN=http://172.17.0.1:8081"]) {
            retry(2) {
                sh "chmod -R 0777 ."
                sh "./run_tests.sh"
                sh "chmod -R 0777 ."
                    }
        }
        }
}

return this;
