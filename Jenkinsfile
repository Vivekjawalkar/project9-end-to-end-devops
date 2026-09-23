pipeline {
    agent any

    environment {
        JAVA_HOME = '/usr/lib/jvm/java-21-amazon-corretto.x86_64'
        PATH = "${JAVA_HOME}/bin:${env.PATH}"

        AWS_REGION = 'us-east-1'
        AWS_ACCOUNT_ID = '434504868934'

        ECR_REPOSITORY = 'project9-end-to-end-devops'
        ECR_REGISTRY = "${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com"
        IMAGE_NAME = "${ECR_REGISTRY}/${ECR_REPOSITORY}"

        ECS_CLUSTER = 'project9-production-cluster'
        ECS_SERVICE = 'project9-production-service'
        ECS_TASK_FAMILY = 'project9-production-task'
        CONTAINER_NAME = 'project9-app'

        IMAGE_TAG = "${GIT_COMMIT}"
        EXECUTION_ROLE_ARN = 'arn:aws:iam::434504868934:role/Project9ECSTaskExecutionRole'

        TARGET_GROUP_NAME = 'project9-tg'
        ECS_SECURITY_GROUP = 'sg-0e0334b5a247014f7'
    }

    stages {

        stage('Checkout') {
            steps {
                echo '=== CHECKOUT ==='
                checkout scm
            }
        }

        stage('Maven Build and Test') {
            steps {
                dir('app') {
                    sh '''
                        set -e

                        echo "=== MAVEN BUILD + TEST ==="

                        mvn clean test package

                        echo "Maven build and tests successful."
                    '''
                }
            }
        }

        stage('Code Quality') {
            steps {
                echo '=== CODE QUALITY ==='
                echo 'SonarQube integration will be enabled in the next stage of Project 9.'
            }
        }

        stage('Docker Build') {
            steps {
                sh '''
                    set -e

                    echo "=== DOCKER BUILD ==="

                    docker build \
                      -t ${IMAGE_NAME}:${IMAGE_TAG} \
                      ./app

                    docker image inspect ${IMAGE_NAME}:${IMAGE_TAG} > /dev/null

                    echo "Docker image:"
                    echo "${IMAGE_NAME}:${IMAGE_TAG}"
                '''
            }
        }

        stage('Login to ECR') {
            steps {
                sh '''
                    set -e

                    echo "=== ECR LOGIN ==="

                    aws ecr get-login-password \
                      --region ${AWS_REGION} | \
                    docker login \
                      --username AWS \
                      --password-stdin ${ECR_REGISTRY}
                '''
            }
        }

        stage('Push Image to ECR') {
            steps {
                sh '''
                    set -e

                    echo "=== PUSH IMAGE ==="

                    docker push ${IMAGE_NAME}:${IMAGE_TAG}

                    echo "Image pushed:"
                    echo "${IMAGE_NAME}:${IMAGE_TAG}"
                '''
            }
        }

        stage('Prepare ECS Task Definition') {
            steps {
                sh '''
                    set -e

                    echo "=== PREPARE ECS TASK DEFINITION ==="

                    cat > task-definition.json <<EOF
{
  "family": "${ECS_TASK_FAMILY}",
  "networkMode": "awsvpc",
  "requiresCompatibilities": [
    "FARGATE"
  ],
  "cpu": "256",
  "memory": "512",
  "executionRoleArn": "${EXECUTION_ROLE_ARN}",
  "containerDefinitions": [
    {
      "name": "${CONTAINER_NAME}",
      "image": "${IMAGE_NAME}:${IMAGE_TAG}",
      "essential": true,
      "portMappings": [
        {
          "containerPort": 8080,
          "protocol": "tcp"
        }
      ],
      "logConfiguration": {
        "logDriver": "awslogs",
        "options": {
          "awslogs-group": "/ecs/project9-production",
          "awslogs-region": "${AWS_REGION}",
          "awslogs-stream-prefix": "ecs"
        }
      }
    }
  ]
}
EOF

                    cat task-definition.json
                '''
            }
        }

        stage('Register Task Definition') {
            steps {
                sh '''
                    set -e

                    echo "=== REGISTER TASK DEFINITION ==="

                    TASK_DEFINITION_ARN=$(aws ecs register-task-definition \
                      --cli-input-json file://task-definition.json \
                      --region ${AWS_REGION} \
                      --query 'taskDefinition.taskDefinitionArn' \
                      --output text)

                    echo "Task Definition:"
                    echo "${TASK_DEFINITION_ARN}"

                    echo "${TASK_DEFINITION_ARN}" > task-definition-arn.txt
                '''
            }
        }

        stage('Deploy to ECS') {
            steps {
                sh '''
                    set -e

                    echo "=== ECS DEPLOYMENT ==="

                    TASK_DEFINITION_ARN=$(cat task-definition-arn.txt)

                    SERVICE_STATUS=$(aws ecs describe-services \
                      --cluster ${ECS_CLUSTER} \
                      --services ${ECS_SERVICE} \
                      --region ${AWS_REGION} \
                      --query 'services[0].status' \
                      --output text 2>/dev/null || true)

                    if [ "${SERVICE_STATUS}" = "ACTIVE" ]; then

                        echo "Existing ECS service found."

                        aws ecs update-service \
                          --cluster ${ECS_CLUSTER} \
                          --service ${ECS_SERVICE} \
                          --task-definition ${TASK_DEFINITION_ARN} \
                          --desired-count 1 \
                          --force-new-deployment \
                          --region ${AWS_REGION}

                    else

                        echo "ECS service does not exist. Creating service."

                        SUBNET_1=$(aws ec2 describe-subnets \
                          --region ${AWS_REGION} \
                          --filters \
                            "Name=tag:Name,Values=project9-public-1" \
                          --query 'Subnets[0].SubnetId' \
                          --output text)

                        SUBNET_2=$(aws ec2 describe-subnets \
                          --region ${AWS_REGION} \
                          --filters \
                            "Name=tag:Name,Values=project9-public-2" \
                          --query 'Subnets[0].SubnetId' \
                          --output text)

                        TARGET_GROUP_ARN=$(aws elbv2 describe-target-groups \
                          --region ${AWS_REGION} \
                          --names ${TARGET_GROUP_NAME} \
                          --query 'TargetGroups[0].TargetGroupArn' \
                          --output text)

                        aws ecs create-service \
                          --cluster ${ECS_CLUSTER} \
                          --service-name ${ECS_SERVICE} \
                          --task-definition ${TASK_DEFINITION_ARN} \
                          --desired-count 1 \
                          --launch-type FARGATE \
                          --platform-version LATEST \
                          --deployment-controller type=ECS \
                          --deployment-configuration \
                            'deploymentCircuitBreaker={enable=true,rollback=true},maximumPercent=200,minimumHealthyPercent=100' \
                          --network-configuration \
                            "awsvpcConfiguration={subnets=[${SUBNET_1},${SUBNET_2}],securityGroups=[${ECS_SECURITY_GROUP}],assignPublicIp=ENABLED}" \
                          --load-balancers \
                            "targetGroupArn=${TARGET_GROUP_ARN},containerName=${CONTAINER_NAME},containerPort=8080" \
                          --region ${AWS_REGION}

                    fi
                '''
            }
        }

        stage('Wait for ECS Stability') {
            steps {
                sh '''
                    set -e

                    echo "=== WAIT FOR ECS STABILITY ==="

                    aws ecs wait services-stable \
                      --cluster ${ECS_CLUSTER} \
                      --services ${ECS_SERVICE} \
                      --region ${AWS_REGION}

                    echo "ECS service is stable."
                '''
            }
        }

        stage('Verify Application') {
            steps {
                sh '''
                    set -e

                    echo "=== VERIFY APPLICATION ==="

                    ALB_DNS=$(aws elbv2 describe-load-balancers \
                      --region ${AWS_REGION} \
                      --names project9-alb \
                      --query 'LoadBalancers[0].DNSName' \
                      --output text)

                    echo "Application URL:"
                    echo "http://${ALB_DNS}"

                    echo "Testing application..."

                    SUCCESS=false

                    for i in $(seq 1 12); do

                        RESPONSE=$(curl -s \
                          --max-time 10 \
                          "http://${ALB_DNS}/health" || true)

                        echo "Attempt ${i}: ${RESPONSE}"

                        if [ "${RESPONSE}" = "UP" ]; then
                            SUCCESS=true
                            break
                        fi

                        sleep 10
                    done

                    if [ "${SUCCESS}" != "true" ]; then
                        echo "ERROR: Application health check failed."
                        exit 1
                    fi

                    echo "Application verification PASSED."
                '''
            }
        }
    }

    post {
        success {
            echo '========================================'
            echo 'PROJECT 9 VERSION DEPLOYMENT SUCCESSFUL'
            echo '========================================'
            echo "Image: ${IMAGE_NAME}:${IMAGE_TAG}"
            echo "ECS Service: ${ECS_SERVICE}"
        }

        failure {
            echo '========================================'
            echo 'PROJECT 9 PIPELINE FAILED'
            echo '========================================'
        }

        always {
            sh '''
                rm -f task-definition.json
                rm -f task-definition-arn.txt
            '''
        }
    }
}
