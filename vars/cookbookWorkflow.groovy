def call(body) {
  // evaluate the body block, and collect configuration into the object
  def config = [:]
  body.resolveStrategy = Closure.DELEGATE_FIRST
  body.delegate = config
  body()

  node() {
    stage('Setup') {
      deleteDir()
      scmProps = checkout scm

      // notify bitbucket that a build is occuring
      notifyStash(scmProps.GIT_COMMIT)
    }

    try {
      stage('Lint') {
        rake('clean')
        rake('style')
      }

      stage('Functional (Kitchen)') {
        echo "Running test-kitchen"
      }

      stage('Upload Chef (dev)') {
        echo "upload to a dev server on green build"
      }

      if (env.BRANCH_NAME =~ /master/) {
        stage('Upload Chef (prod)') {
          echo """
            Upload to a chef prod server for master workflows. 
            Best to overwrite the chef dev server with this version too.
          """
        }

        stage('Tag Repo') {
          echo "Tag repo with version of the cookbook"
        }
      }

      // have to explicitly set this before sending stash notification
      currentBuild.result = 'SUCCESS'
    }
    catch(err){
      // have to explicitly set this before sending stash notification
      currentBuild.result = 'FAILED'
      throw err
    }
    finally {
      notifyStash(scmProps.GIT_COMMIT)
    }
  }
}

// // To run the rake commands in the rakefile
def rake(command) {
  bat "chef exec rake ${command} CI=false"
}

// Configuring the Stash Notifier in Jenkins to talk to BitBucket
def notifyStash(String sha) {
  step([
    $class: 'StashNotifier',
    commitSha1: sha, 
    considerUnstableAsSuccess: false,
    credentialsId: 'your-cred-id', // Credential in Jenkins corresponds to the scm user (safe for source control)
    disableInprogressNotification: false,
    ignoreUnverifiedSSLPeer: true,
    includeBuildNumberInKey: false,
    prependParentProjectKey: false,
    projectKey: '',
    stashServerBaseUrl: 'https://bitbucket.company.com' // URL to BitBucket
  ])
}
