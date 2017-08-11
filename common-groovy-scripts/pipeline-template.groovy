node("cd") {
    git "https://github.com/cloudbees/${serviceName}.git"
    def dir = pwd()
    sh "mkdir -p ${dir}/db"
    sh "chmod 0777 ${dir}/db"

    stage "pre-deployment tests"
    def tests = docker.image("${registry}/${serviceName}-tests")
    tests.inside("-v ${dir}/db:/data/db") {
        sh "./run_tests.sh"
    }

    stage "build"
    def service = docker.build "${registry}/${serviceName}"
    service.push()
    stash includes: "docker-compose*.yml", name: "docker-compose"
}

checkpoint "deploy"

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
    sh "curl http://localhost:8080/docker-traceability/submitContainerStatus \
        --data-urlencode inspectData=\"\$(docker inspect booksms_app_1)\""
    sleep 2
}

node("cd") {
    stage "post-deployment tests"
    def tests = docker.image("${registry}/${serviceName}-tests")
    tests.inside() {
        withEnv(["TEST_TYPE=integ", "DOMAIN=http://${domain}:8081"]) {
            retry(2) {
                sh "./run_tests.sh"
            }
        }
    }
}