/*
 * Copyright (c) 2019, Lucas <https://github.com/Lucwousin>
 * All rights reserved.
 *
 * This code is licensed under GPL3, see the complete license in
 * the LICENSE file in the root directory of this source tree.
 */
package com.openosrs.injector

import com.openosrs.injector.injection.InjectTaskHandler
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

@CacheableTask
abstract class Inject : DefaultTask() {
    @get:Nested
    abstract val extension: InjectExtension

    @get:OutputFile
    abstract val output: RegularFileProperty

    @get:OutputFile
    abstract val hash: RegularFileProperty

    @TaskAction
    fun inject() {
        val vanilla = extension.vanilla.get().asFile
        val rsclient = extension.rsclient.get().asFile
        val mixins = extension.mixins.get().asFile
        val rsapi = project.zipTree(extension.rsapi)

        val injector: InjectTaskHandler = Injection(
                vanilla,
                rsclient,
                mixins,
                rsapi,
                if (extension.development.isPresent) extension.development.get() else true,
                if (extension.skip.isPresent) extension.skip.get() else ""
        )

        injector.inject()

        injector.save(output.get().asFile)
        injector.hash(hash.get().asFile, vanilla)
    }
}