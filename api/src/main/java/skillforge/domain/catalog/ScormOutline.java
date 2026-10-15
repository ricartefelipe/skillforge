package skillforge.domain.catalog;

import java.util.List;

public record ScormOutline(String path, List<Step> steps) {

    public ScormOutline {
        steps = List.copyOf(steps);
    }

    public record Step(int position, String title, String path, boolean open) {
    }

}
