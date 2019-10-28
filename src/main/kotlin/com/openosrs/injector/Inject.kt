package com.openosrs.injector

import com.openosrs.injector.injection.InjectTaskHandler
import com.openosrs.injector.rsapi.RSApi
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

open class Inject: DefaultTask() {
    @InputFile
    val vanilla = project.objects.fileProperty()

    @InputDirectory
    val rsclient = project.objects.directoryProperty()

    @InputDirectory
    val mixins = project.objects.directoryProperty()

    @InputDirectory
    val rsapi = project.objects.directoryProperty()

    @OutputFile
    val output = project.objects.fileProperty().convention {
        project.file("${project.buildDir}/libs/${project.name}-${project.version}")
    }

    @TaskAction
    fun inject() {
        val vanilla = this.vanilla.get().asFile
        val rsclient = this.rsclient.asFileTree
        val mixins = this.mixins.asFileTree
        val rsapi = this.rsapi.asFileTree
        val output = this.output.asFile

        val injector: InjectTaskHandler = Injection(vanilla, rsclient, rsapi, mixins)

        injector.inject()

        injector.save(output.get())
    }
}