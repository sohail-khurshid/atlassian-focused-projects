import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import groovy.json.JsonBuilder
import java.nio.file.Files
import java.nio.file.Paths

// Load Configuration
def config = Config.load()
def cloud = config.jira_cloud
def fileConfig = config.files

// Setup Logging-style output
def log = { msg -> println "${new Date().format('yyyy-MM-dd HH:mm:ss')} - INFO - ${msg}" }
def logError = { msg -> System.err.println "${new Date().format('yyyy-MM-dd HH:mm:ss')} - ERROR - ${msg}" }

// Auth Header for Jira Cloud (Email + API Token)
def authHeader = "Basic " + "${cloud.user_email}:${cloud.user_token}".bytes.encodeBase64().toString()

/**
 * Replicates the Account ID lookup from Python
 */
def getAccountId(email) {
    def url = "${cloud.domain}/rest/api/3/user/search?query=${URLEncoder.encode(email, 'UTF-8')}"
    def conn = new URL(url).openConnection() as HttpURLConnection
    conn.setRequestProperty("Authorization", authHeader)
    conn.setRequestProperty("Accept", "application/json")

    if (conn.responseCode == 200) {
        def users = new JsonSlurper().parseText(conn.inputStream.text)
        return users ? users[0].accountId : null
    }
    return null
}

/**
 * State Management: Replicates migration_state.json logic
 */
def stateFile = new File("migration_state.json")
def state = stateFile.exists() ? new JsonSlurper().parse(stateFile) : [processed_issues: [:]]

def saveState = {
    stateFile.write(JsonOutput.prettyPrint(JsonOutput.toJson(state)))
}

// Main Execution
def dataFile = new File(fileConfig.data_file)
if (!dataFile.exists()) {
    logError "Data file not found: ${fileConfig.data_file}"
    return
}

def data = new JsonSlurper().parse(dataFile)

data.issues.each { issue ->
    log "Processing issue: ${issue.key}"
    
    // Skip if already fully processed in state file
    if (state.processed_issues[issue.key]?.fully_migrated) {
        log "Skipping ${issue.key} - already migrated."
        return
    }

    try {
        // 1. Create Issue
        def newIssueKey = state.processed_issues[issue.key]?.cloud_key
        if (!newIssueKey) {
            def payload = [
                fields: [
                    project: [key: cloud.project.key],
                    summary: issue.summary,
                    description: [
                        type: "doc",
                        version: 1,
                        content: [[type: "paragraph", content: [[type: "text", text: issue.description ?: "No description provided."]]]]
                    ],
                    issuetype: [name: issue.type],
                    priority: [name: issue.priority]
                ]
            ]
            
            // Map Assignee if possible
            def accId = getAccountId(issue.assignee_email)
            if (accId) payload.fields.assignee = [accountId: accId]

            def conn = new URL("${cloud.domain}/rest/api/3/issue").openConnection() as HttpURLConnection
            conn.with {
                doOutput = true
                requestMethod = 'POST'
                setRequestProperty("Authorization", authHeader)
                setRequestProperty("Content-Type", "application/json")
                outputStream.withWriter { it << JsonOutput.toJson(payload) }
            }

            if (conn.responseCode == 201) {
                newIssueKey = new JsonSlurper().parseText(conn.inputStream.text).key
                state.processed_issues[issue.key] = [cloud_key: newIssueKey, fully_migrated: false]
                saveState()
                log "Created Cloud Issue: ${newIssueKey}"
            } else {
                logError "Failed to create issue ${issue.key}: ${conn.errorStream?.text}"
                return
            }
        }

        // 2. Add Comments
        if (!state.processed_issues[issue.key].comments_added) {
            issue.comments.each { comment ->
                def cUrl = "${cloud.domain}/rest/api/3/issue/${newIssueKey}/comment"
                def cPayload = [
                    body: [
                        type: "doc", version: 1,
                        content: [[type: "paragraph", content: [[type: "text", text: "Migrated from ${comment.author}: ${comment.body}"]]]]
                    ]
                ]
                def cConn = new URL(cUrl).openConnection() as HttpURLConnection
                cConn.with {
                    doOutput = true
                    setRequestProperty("Authorization", authHeader)
                    setRequestProperty("Content-Type", "application/json")
                    outputStream.withWriter { it << JsonOutput.toJson(cPayload) }
                }
            }
            state.processed_issues[issue.key].comments_added = true
            saveState()
        }

        // 3. Upload Attachments
        if (!state.processed_issues[issue.key].attachments_added) {
            def attachmentDir = new File("${fileConfig.attachments_folder}/${issue.key}")
            if (attachmentDir.exists()) {
                attachmentDir.listFiles().each { file ->
                    uploadToCloud(newIssueKey, file, authHeader, cloud.domain)
                }
            }
            state.processed_issues[issue.key].attachments_added = true
            state.processed_issues[issue.key].fully_migrated = true
            saveState()
        }

    } catch (Exception e) {
        logError "Error processing ${issue.key}: ${e.message}"
        if (cloud.stop_on_failure) return
    }
}

/**
 * Helper for Multipart/Form-Data Attachment Uploads in Cloud
 */
def uploadToCloud(issueKey, file, auth, domain) {
    def url = "${domain}/rest/api/3/issue/${issueKey}/attachments"
    def boundary = "---" + System.currentTimeMillis()
    def conn = new URL(url).openConnection() as HttpURLConnection
    conn.with {
        doOutput = true
        requestMethod = 'POST'
        setRequestProperty("Authorization", auth)
        setRequestProperty("X-Atlassian-Token", "no-check")
        setRequestProperty("Content-Type", "multipart/form-data; boundary=${boundary}")
    }

    conn.outputStream.withWriter { w ->
        w << "--${boundary}\r\n"
        w << "Content-Disposition: form-data; name=\"file\"; filename=\"${file.name}\"\r\n\r\n"
        w.flush()
        file.withInputStream { is -> conn.outputStream << is }
        w << "\r\n--${boundary}--\r\n"
    }
    return conn.responseCode == 200
}