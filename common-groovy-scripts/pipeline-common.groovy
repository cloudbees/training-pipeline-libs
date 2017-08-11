def runPreDeploymentTests(serviceName, registry) {
    stage "pre-deployment tests"
    def dir = pwd()
    sh "mkdir -p ${dir}/db"
    sh "chmod 0777 ${dir}/db"
    def tests = docker.image("${registry}/${serviceName}-tests")
    tests.pull()
    tests.inside("-v ${dir}/db:/data/db") {
        sh "./run_tests.sh"
    }
}

def build(serviceName, registry) {
    stage "build"
    def service = docker.build "${registry}/${serviceName}"
    service.push()
    stash includes: "docker-compose*.yml", name: "docker-compose"
}

def deploy(serviceName, registry) {
    node("production") {
        stage "deploy"
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
    stage "post-deployment tests"
    def tests = docker.image("${registry}/${serviceName}-tests")
    tests.inside() {
        withEnv(["TEST_TYPE=integ", "DOMAIN=${domain}"]) {
            retry(2) {
                sh "./run_tests.sh"
            }
        }
    }
}

return this;
