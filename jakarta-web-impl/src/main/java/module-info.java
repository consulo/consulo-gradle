/**
 * @author VISTALL
 * @since 04/07/2023
 */
module org.jetbrains.plugins.gradle.jakarta.web.impl {
    requires org.jetbrains.plugins.gradle.api;
    requires org.jetbrains.plugins.gradle.tooling;

    opens org.jetbrains.plugins.gradle.integrations.javaee;
}