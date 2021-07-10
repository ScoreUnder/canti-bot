package score.discord.canti.jdamocks;

import net.dv8tion.jda.api.JDA;

import java.util.Arrays;
import java.util.List;
import javax.annotation.Nonnull;

// https://github.com/lampepfl/dotty/issues/13040
public abstract class FakeJdaShim implements JDA {
    abstract void addEventListener(@Nonnull List<?> listeners);
    abstract void removeEventListener(@Nonnull List<?> listeners);

    @Override
    public void addEventListener(Object... listeners) {
        addEventListener(Arrays.asList(listeners));
    }

    @Override
    public void removeEventListener(Object... listeners) {
        removeEventListener(Arrays.asList(listeners));
    }
}
