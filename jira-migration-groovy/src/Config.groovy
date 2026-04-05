import groovy.yaml.YamlSlurper

class Config {
    static Map load() {
        def configFile = new File('settings.yaml')
        if (!configFile.exists()) {
            throw new FileNotFoundException("settings.yaml not found. Please create one from settings.template.yaml")
        }
        return new YamlSlurper().parse(configFile)
    }
}