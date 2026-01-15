// Utility functions
List<String> getNativeBuildLog(String fileName) {
    File f = new File(basedir, fileName)
    assert f.exists()
    def lines = f.text.readLines()
    return dropUntil(lines, 'prepare-cache')
}

List<String> dropUntil(List<String> lines, String marker) {
    def index = lines.findIndexOf { it.contains(marker) }
    return lines.drop(index)
}

List<String> assertAndCrop(List<String> lines, String marker) {
    assert lines.any {it.contains(marker)}
    return dropUntil(lines, marker)
}

List<String> assertCacheIsConfigured (List<String> log) {
    log = assertAndCrop(log, 'Found native image bundle')
    log = assertAndCrop(log, 'Native build cache configured')
    return log
}

void assertBuildCacheMiss(String logFile) {
    println("Verifying build cache miss on ${logFile}...")
    def log = getNativeBuildLog(logFile)
    println("------------------------------------------")
    println("Inspecting: ${log}")
    log = assertCacheIsConfigured(log)
    println("------------------------------------------")
    println("Inspecting: ${log}")
    assert log.any {it.contains('Local cache miss')}
}

void assertBuildCacheHit(String logFile) {
    println("Verifying build cache hit on ${logFile}...")
    def log = getNativeBuildLog(logFile)
    println("------------------------------------------")
    println("Inspecting: ${log}")
    log = assertCacheIsConfigured(log)
    println("------------------------------------------")
    println("Inspecting: ${log}")
    assert log.any {it.contains('Local cache hit')}
}

// Assertions
assertBuildCacheMiss('01-prepare-cache.log')
assertBuildCacheHit('02-hit-cache.log')

println('Verification succeeded')
