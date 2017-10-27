package com.antfortune.freeline

import org.apache.commons.io.FileUtils
import org.gradle.api.GradleException
import org.gradle.api.Project

/**
 * Created by huangyong on 16/11/21.
 */
class FreelineDownloader {

    private static
    final String MAVEN_URL = "https://dl.bintray.com/veyhey/github/com/veyhey/freeline/freeline/%s/freeline-%s.zip"
    private static final String PARAM_LOCAL = "freelineLocal"

    public static void execute(Project project, String version) {
        String localPath = FreelineUtils.getProperty(project, PARAM_LOCAL)
        String targetUrl = getDownloadUrl(MAVEN_URL, version)

        def ant = new AntBuilder()
        if (FreelineUtils.isEmpty(localPath)) {
            println "Downloading release pack from ${targetUrl}"
            println "Please wait a minute..."
            def downloadFile = new File(project.rootDir, "freeline.zip.tmp")
            if (downloadFile.exists()) {
                downloadFile.delete()
            }

            ant.get(src: targetUrl, dest: downloadFile)
            downloadFile.renameTo("freeline.zip")
            println 'download success.'
        } else {
            File localFile = getRealFile(project.rootDir.absolutePath, localPath)
            if (localFile == null) {
                throw new GradleException("File not found for freelineLocal: -PfreelineLocal=${localPath}")
            }

            File targetFile = new File(project.rootDir, "freeline.zip")
            FileUtils.copyFile(localFile, targetFile)
            println "Download freeline.zip from disk path: ${localFile.absolutePath}"
        }

        def freelineDir = new File(project.rootDir, "freeline")
        if (freelineDir.exists()) {
            FileUtils.deleteDirectory(freelineDir)
            println 'removing existing freeline directory'
        }
        ant.unzip(src: "freeline.zip", dest: project.rootDir.absolutePath) {
            mapper {
                globmapper(from: "freeline-$version/*", to: "freeline/*")
            }
        }
        println 'unziped freeline.zip.'

        if (FreelineUtils.isWindows()) {
            FileUtils.deleteQuietly(new File(project.rootDir, "freeline_core"))
            FileUtils.deleteQuietly(new File(project.rootDir, "freeline.py"))
            FileUtils.copyDirectory(new File(freelineDir, "freeline_core"), new File(project.rootDir, "freeline_core"));
            FileUtils.copyFile(new File(freelineDir, "freeline.py"), new File(project.rootDir, "freeline.py"))
        } else {
            Runtime.getRuntime().exec("chmod -R +x freeline")
            Runtime.getRuntime().exec("ln -s freeline/freeline.py freeline.py")
        }

        def freelineZipFile = new File(project.rootDir, "freeline.zip")
        if (freelineZipFile.exists()) {
            freelineZipFile.delete()
        }
    }

    private static File getRealFile(String rootDirPath, String freelineLocal) {
        if (!FreelineUtils.isEmpty(freelineLocal)) {
            File localFile;
            if (freelineLocal.contains(File.separator)) {
                localFile = new File(freelineLocal)
            } else {
                localFile = new File(rootDirPath, freelineLocal)
            }
            return localFile.exists() ? localFile : null
        }
        return null
    }

    private static String getDownloadUrl(String mavenUrl, String version) {
        return String.format(mavenUrl, version, version);
    }


}
