package yt.ren.slate.game;

import net.minecraft.entity.Entity;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.scoreboard.team.AbstractTeam;
import net.minecraft.scoreboard.team.Team;

public class NameColors {
    public static boolean nameCharacter(int value) {
        return value == '_' || ((value - '0') & 0xFFFF) < 10 || (((value & 0xffdf) - 'A') & 0xFFFF) < 26;
    }

    private static boolean colorCharacter(int value) {
        return ((value - '0') & 0xFFFF) < 10 || ((value - 'a') & 0xFFFF) < 6 || value == 'r';
    }

    public static int prefixColor(String text) {
        int length = text.length();
        int result = 0;
        boolean formatting = false;
        boolean foundName = false;
        for (int index = 0; index < length; ++index) {
            int value = text.charAt(index);
            if (value != 0xa7) {
                if (formatting) {
                    if (colorCharacter(value)) {
                        result = value;
                    }
                } else if (nameCharacter(value)) {
                    foundName = true;
                    break;
                }
            }
            formatting = value == 0xa7;
            length = text.length();
        }
        return foundName ? result : 0;
    }

    private static int lastColor(String text) {
        int length = text.length();
        if (length == 0) {
            return 'f';
        }
        int result = 'f';
        int candidate = 0;
        boolean candidateValid = false;
        for (int index = length - 1; index >= 0; --index) {
            int value = text.charAt(index);
            if (value == 0xa7) {
                if (candidateValid) {
                    result = candidate;
                    break;
                }
                candidateValid = false;
            } else {
                candidateValid = colorCharacter(value);
                if (candidateValid) {
                    candidate = value;
                }
            }
        }
        return result;
    }

    public static int teamColor(Team team) {
        String text = team.prefix;
        return text != null ? lastColor(text) : 'f';
    }

    public static int entityColor(Entity entity) {
        int color = prefixColor(entity.getName());
        if (color != 0) {
            return color;
        }
        if (!(entity instanceof LivingEntity)) {
            return 'f';
        }
        AbstractTeam team = ((LivingEntity) entity).getScoreboardTeam();
        return team instanceof Team ? teamColor((Team) team) : 'f';
    }
}
