/*
 * Copyright 2008 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gradle.api.tasks.diagnostics;

import org.gradle.api.Incubating;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.diagnostics.internal.PropertyReportRenderer;
import org.gradle.api.tasks.diagnostics.internal.ReportRenderer;
import org.gradle.api.tasks.options.Option;
import org.gradle.internal.serialization.Cached;
import org.gradle.work.DisableCachingByDefault;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Displays the properties of a project. An instance of this type is used when you execute the {@code properties} task
 * from the command-line.
 */
@DisableCachingByDefault(because = "Not worth caching")
public class PropertyReportTask extends ConventionReportTask {
    private PropertyReportRenderer renderer = new PropertyReportRenderer();
    private final Property<String> property = getProject().getObjects().property(String.class);

    private final Cached<PropertyReportTask.PropertyReportModel> model = Cached.of(this::computePropertyReportModel);

    /**
     * Defines a specific property to report. If not set then all properties will appear in the report.
     *
     * @since 7.5
     */
    @Incubating
    @Input
    @Optional
    @Option(option = "property", description = "A specific property to output")
    public Property<String> getProperty() {
        return property;
    }

    @Override
    public ReportRenderer getRenderer() {
        return renderer;
    }

    public void setRenderer(PropertyReportRenderer renderer) {
        this.renderer = renderer;
    }

    private PropertyReportTask.PropertyReportModel computePropertyReportModel() {
        PropertyReportModel model = new PropertyReportModel();
        Map<String, ?> projectProperties = getProject().getProperties();
        if (property.isPresent()) {
            String propertyName = property.get();
            if ("properties".equals(propertyName)) {
                model.putProperty(propertyName, "{...}");
            } else {
                model.putProperty(propertyName, projectProperties.get(propertyName));
            }
        } else {
            for (Map.Entry<String, ?> entry : projectProperties.entrySet()) {
                if ("properties".equals(entry.getKey())) {
                    model.putProperty(entry.getKey(), "{...}");
                } else {
                    model.putProperty(entry.getKey(), entry.getValue());
                }
            }
        }
        return model;
    }

    private static class PropertyReportModel {
        private final Map<String, String> properties = new TreeMap<>();
        private final List<PropertyWarning> warnings = new ArrayList<>();

        private void putProperty(String name, @Nullable Object value) {
            String strValue;
            try {
                strValue = String.valueOf(value);
            } catch (Exception e) {
                String valueClass = value != null ? String.valueOf(value.getClass()) : "null";
                warnings.add(new PropertyWarning(name, valueClass, e));
                strValue = valueClass + " [Rendering failed]";
            }
            properties.put(name, strValue);
        }
    }

    private static class PropertyWarning {
        private final String name;
        private final String valueClass;
        private final Exception exception;

        private PropertyWarning(String name, String valueClass, Exception exception) {
            this.name = name;
            this.valueClass = valueClass;
            this.exception = exception;
        }
    }

    @TaskAction
    void action() {
        for (PropertyWarning warning : model.get().warnings) {
            getLogger().warn("Rendering of the property '{}' with value type '{}' failed with exception", warning.name, warning.valueClass, warning.exception);
        }
        renderer.setClientMetaData(getClientMetaData());
        renderer.setOutput(getTextOutputFactory().create(getClass()));
        for (Map.Entry<String, String> entry : model.get().properties.entrySet()) {
            renderer.addProperty(entry.getKey(), entry.getValue());
        }
    }
}
