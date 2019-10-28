package com.openosrs.injector

import org.gradle.api.Project
import org.gradle.api.model.ObjectFactory
import javax.inject.Inject

open class Injextention(project: Project) {
    val vanilla = project.objects.fileProperty()
    val rsclient = project.objects.directoryProperty()
    val mixins = project.objects.directoryProperty()
    val rsapi = project.objects.directoryProperty()
    val output = project.objects.fileProperty()
}

