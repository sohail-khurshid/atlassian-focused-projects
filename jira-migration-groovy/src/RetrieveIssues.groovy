import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import java.nio.file.Files
import java.nio.file.Paths

// Load Configuration
def config = Config.load()
def dcConfig = config.jira_datacenter
def fileConfig = config.files

def baseAttachmentsDir = fileConfig.attachments_folder ?: "attachments"
new File(baseAttachmentsDir).mkdirs()

def result = [issues: []]

try {
    println "Searching issues on ${dcConfig.domain}..."
    
    // Construct JQL Search URL
    def encodedQuery = URLEncoder.encode(dcConfig.query, "UTF-8")
    def url = "${dcConfig.domain}/rest/api/2/search?jql=${encodedQuery}&maxResults=${dcConfig.max_results ?: 100}"
    
    def connection = new URL(url).openConnection() as HttpURLConnection
    connection.setRequestProperty("Authorization", "Bearer ${dcConfig.token}")
    connection.setRequestProperty("Accept", "application/json")

    if (connection.responseCode == 200) {
        def json = new JsonSlurper().parseText(connection.inputStream.text)
        println "Found ${json.issues.size()} issues"

        json.issues.each { issue ->
            println "Processing issue: ${issue.key}"
            
            // Create directory for issue attachments
            def issueDir = new File("${baseAttachmentsDir}/${issue.key}")
            issueDir.mkdirs()

            // Extract Comments
            def comments = issue.fields.comment.comments.collect { c ->
                [author: c.author.displayName, created: c.created, body: c.body]
            }

            // Extract & Download Attachments
            def attachments = issue.fields.attachment.collect { a ->
                def localFile = new File(issueDir, a.filename)
                if (!localFile.exists()) {
                    downloadFile(a.content, localFile, dcConfig.token)
                }
                return [filename: a.filename, content: a.content]
            }

            // Build Issue Metadata (Mapping fields from your Python script)
            def issueData = [
                key: issue.key,
                summary: issue.fields.summary,
                status: issue.fields.status.name,
                priority: issue.fields.priority?.name ?: "None",
                assignee: issue.fields.assignee?.displayName ?: "Unassigned",
                assignee_email: issue.fields.assignee?.emailAddress ?: "Unassigned",
                created: issue.fields.created,
                updated: issue.fields.updated,
                reporter: issue.fields.reporter?.displayName ?: "Unknown",
                labels: issue.fields.labels,
                type: issue.fields.issuetype.name,
                description: issue.fields.description,
                components: issue.fields.components.collect { it.name },
                comments: comments,
                attachments: attachments
            ]
            result.issues << issueData
        }

        // Save to JSON file
        def outputFile = new File(fileConfig.data_file ?: "jira_issues_response.json")
        outputFile.write(JsonOutput.prettyPrint(JsonOutput.toJson(result)))
        println "Data saved to ${outputFile.name}"

    } else {
        println "Error: ${connection.responseCode}"
        println connection.errorStream?.text
    }
} catch (Exception e) {
    println "Script failed: ${e.message}"
    e.printStackTrace()
}

/**
 * Helper to download binary content from Jira
 */
def downloadFile(String fileUrl, File destination, String token) {
    try {
        def conn = new URL(fileUrl).openConnection() as HttpURLConnection
        conn.setRequestProperty("Authorization", "Bearer ${token}")
        
        if (conn.responseCode == 200) {
            destination.withOutputStream { it << conn.inputStream }
            println "   Downloaded: ${destination.name}"
        }
    } catch (Exception e) {
        println "   Failed to download ${destination.name}: ${e.message}"
    }
}