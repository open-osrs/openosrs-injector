/*
 * Copyright (c) 2019, Lucas <https://github.com/Lucwousin>
 * All rights reserved.
 *
 * This code is licensed under GPL3, see the complete license in
 * the LICENSE file in the root directory of this source tree.
 */
package com.openosrs.injector

import org.gradle.api.Plugin
import org.gradle.api.Project

class InjectPlugin: Plugin<Project> {
    override fun apply(project: Project) {
        val extension = project.extensions.create("injector", Injextention::class.java, project)

        project.tasks.create("inject", Inject::class.java) {
            it.vanilla.set(extension.vanilla)
            it.rsclient.set(extension.rsclient)
            it.mixins.set(extension.mixins)
            it.rsapi.set(extension.rsapi)
            if (extension.output.isPresent) {
                it.output.set(extension.output)
            }
        }
    }
}