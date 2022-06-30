/*
 * Copyright (c) 2019, Lucas <https://github.com/Lucwousin>
 * All rights reserved.
 *
 * This code is licensed under GPL3, see the complete license in
 * the LICENSE file in the root directory of this source tree.
 */
package com.openosrs.injector

import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*

interface InjectExtension {
    @get:Input
    @get:Optional
    val development: Property<Boolean>

    @get:Input
    @get:Optional
    val skip: Property<String>

    @get:[InputFile PathSensitive(PathSensitivity.NONE)]
    val vanilla: RegularFileProperty

    @get:[InputFile PathSensitive(PathSensitivity.NONE)]
    val rsclient: RegularFileProperty

    @get:[InputFile PathSensitive(PathSensitivity.NONE)]
    val mixins: RegularFileProperty

    @get:[InputFile PathSensitive(PathSensitivity.NONE)]
    val rsapi: RegularFileProperty
}

