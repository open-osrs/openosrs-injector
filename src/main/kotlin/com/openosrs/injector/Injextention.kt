package com.openosrs.injector

import org.gradle.api.Project

open class Injextention(project: Project) {
    val vanilla = project.objects.fileProperty()
    val rsclient = project.objects.fileProperty()
    val mixins = project.objects.fileProperty()
    val rsapi = project.objects.fileProperty()
    val output = project.objects.fileProperty()
}

