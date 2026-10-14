package skillforge.infrastructure.persistence;

import org.springframework.stereotype.Component;

import skillforge.domain.catalog.LessonBounds;
import skillforge.domain.catalog.LessonContent;
import skillforge.infrastructure.config.SkillforgeProperties;

@Component
class ConfiguredLessonBounds implements LessonBounds {

    private final SkillforgeProperties properties;

    ConfiguredLessonBounds(SkillforgeProperties properties) {
        this.properties = properties;
    }

    @Override
    public LessonContent.Limits current() {
        SkillforgeProperties.Catalog catalog = properties.getCatalog();
        return new LessonContent.Limits(
                catalog.getMarkdownMax(),
                catalog.getPdfMax().toBytes(),
                catalog.getVideoMax().toBytes());
    }

}
