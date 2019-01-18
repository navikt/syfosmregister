#!/usr/bin/env groovy

pipeline {
    agent any

     environment {
           APPLICATION_NAME = 'syfosmregister'
           DOCKER_SLUG = 'syfo'
           DISABLE_SLACK_MESSAGES = true
       }

     stages {
        stage('initialize') {
            steps {
                init action: 'gradle'
            }
        }
        stage('build') {
            steps {
                sh './gradlew build -x test'
            }
        }
        stage('run tests (unit & intergration)') {
            steps {
                sh './gradlew test'
            }
        }
        stage('create uber jar') {
            steps {
                sh './gradlew shadowJar'
                slackStatus status: 'passed'
            }
        }
       stage('push docker image') {
              steps {
                  dockerUtils action: 'createPushImage'
              }
         }
        stage('Create kafka topics') {
            steps {
                sh 'echo TODO'
                // TODO
            }
        }
        stage('deploy to preprod') {
            steps {
                deployApp action: 'kubectlApply', cluster: 'preprod-fss', file: 'config/preprod/configmap.yaml'
                deployApp action: 'kubectlDeploy', cluster: 'preprod-fss', placeholders: ["config_file" : "application-preprod.json"]
                    }
                }
        stage('deploy to production') {
            when { environment name: 'DEPLOY_TO', value: 'production' }
            steps {
                deployApp action: 'kubectlApply', cluster: 'prod-fss', file: 'config/prod/configmap.yaml'
                deployApp action: 'kubectlDeploy', cluster: 'prod-fss', placeholders: ["config_file" : "application-prod.json"]
                githubStatus action: 'tagRelease'
                    }
                }
        }
        post {
            always {
                postProcess action: 'always'
            }
            success {
                postProcess action: 'success'
            }
            failure {
                postProcess action: 'failure'
            }
        }
}
