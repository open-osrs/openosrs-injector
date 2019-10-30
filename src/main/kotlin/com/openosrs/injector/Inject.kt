package com.openosrs.injector

import com.openosrs.injector.injection.InjectTaskHandler
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

open class Inject: DefaultTask() {
    @InputFile
    val vanilla = project.objects.fileProperty()

    @InputFile
    val rsclient = project.objects.fileProperty()

    @InputFile
    val mixins = project.objects.fileProperty()

    @InputFile
    val rsapi = project.objects.fileProperty()

    @OutputFile
    val output = project.objects.fileProperty().convention {
        project.file("${project.buildDir}/libs/${project.name}-${project.version}.jar")
    }

    @TaskAction
    fun inject() {
        val vanilla = this.vanilla.get().asFile
        val rsclient = this.rsclient.get().asFile
        val mixins = this.mixins.get().asFile
        val rsapi = project.zipTree(this.rsapi)
        val output = this.output.asFile

        val injector: InjectTaskHandler = Injection(vanilla, rsclient, mixins, rsapi)

        injector.inject()

        injector.save(output.get())
    }
}