/**
 * Sonatype Nexus (TM) Open Source Version
 * Copyright (c) 2007-2012 Sonatype, Inc.
 * All rights reserved. Includes the third-party code listed at http://links.sonatype.com/products/nexus/oss/attributions.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse Public License Version 1.0,
 * which accompanies this distribution and is available at http://www.eclipse.org/legal/epl-v10.html.
 *
 * Sonatype Nexus (TM) Professional Version is available from Sonatype, Inc. "Sonatype" and "Sonatype Nexus" are trademarks
 * of Sonatype, Inc. Apache Maven is a trademark of the Apache Software Foundation. M2eclipse is a trademark of the
 * Eclipse Foundation. All other trademarks are the property of their respective owners.
 */
package org.sonatype.nexus.yum.internal.task;

import static org.sonatype.nexus.formfields.FormField.MANDATORY;
import static org.sonatype.nexus.formfields.FormField.OPTIONAL;
import static org.sonatype.nexus.yum.internal.task.GenerateMetadataTask.PARAM_REPO_DIR;
import static org.sonatype.nexus.yum.internal.task.GenerateMetadataTask.PARAM_REPO_ID;

import java.util.Arrays;
import java.util.List;
import javax.inject.Named;
import javax.inject.Singleton;

import org.sonatype.nexus.formfields.FormField;
import org.sonatype.nexus.formfields.RepoComboFormField;
import org.sonatype.nexus.formfields.StringTextFormField;
import org.sonatype.nexus.tasks.descriptors.AbstractScheduledTaskDescriptor;

@Named( GenerateMetadataTask.ID )
@Singleton
public class GenerateMetadataTaskDescriptor
    extends AbstractScheduledTaskDescriptor
{

    public static final String NAME = "Yum: Generate Metadate";

    private final RepoComboFormField repoField = new RepoComboFormField(
        PARAM_REPO_ID, "Repostiory for createrepo",
        "Maven Repository for which the yum metadata is generated via createrepo.",
        MANDATORY
    );

    private final StringTextFormField outputField = new StringTextFormField(
        PARAM_REPO_DIR,
        "Optional Output Directory",
        "Directory which should contain the yum metadata after generation."
            + " If not set, yum will generate the metadata into the root directory of the selected repository.",
        OPTIONAL
    );

    @Override
    public String getId()
    {
        return GenerateMetadataTask.ID;
    }

    @Override
    public String getName()
    {
        return NAME;
    }

    @Override
    public List<FormField> formFields()
    {
        return Arrays.<FormField>asList( repoField, outputField );
    }

}
