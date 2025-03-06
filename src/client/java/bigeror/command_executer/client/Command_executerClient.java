package bigeror.command_executer.client;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.caoccao.javet.interop.NodeRuntime;
import com.caoccao.javet.interop.V8Host;
import com.caoccao.javet.interop.converters.JavetProxyConverter;
import com.caoccao.javet.values.reference.V8ValueFunction;
import com.caoccao.javet.values.reference.V8ValueReference;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientWorldEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.block.entity.CommandBlockBlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;

import net.minecraft.network.packet.c2s.play.UpdateCommandBlockC2SPacket;
import net.minecraft.scoreboard.ScoreHolder;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardObjective;

public class Command_executerClient implements ClientModInitializer {
    
    public static final Logger log = LoggerFactory.getLogger(Command_executerClient.class);
    private final SimpleConfig config = SimpleConfig.of("command executor").provider(this::createConfig).request();
    private final String path = config.getOrDefault("path", FabricLoader.getInstance().getGameDir().resolve("executor/main.js").toString());
    private final boolean info = config.getOrDefault("info_enabled", true);
    public static NodeRuntime runtime;
    
    private String createConfig(String namespace) {
        return """
                # This is the main config file for the command executor mod.
                path=""" + FabricLoader.getInstance().getGameDir().resolve("executor/main.js").toString() + """
                
                info_enabled=true
                """;
                        
    }

    private interface executable {
        void executeClient(MinecraftClient client);
    }

    private void createBind(String name, InputUtil.Type type, Integer key, executable function) {
        KeyBinding keyBind = KeyBindingHelper.registerKeyBinding(new KeyBinding(name, type, key, "command executor"));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (keyBind.wasPressed()) {
                function.executeClient(client);
            }
        });
    }

    private void executeClient(MinecraftClient client) {
        new Thread(() -> {
            HitResult hit = client.cameraEntity.raycast(20, 0, false);
            if (hit.getType() == HitResult.Type.BLOCK) {
                BlockHitResult blockHit = (BlockHitResult)hit;
                if (client.world.getBlockEntity(blockHit.getBlockPos()) != null) {
                    if (client.world.getBlockEntity(blockHit.getBlockPos()).getType() != BlockEntityType.COMMAND_BLOCK) {
                        world.pos = null;
                    } else {
                        world.pos = blockHit.getBlockPos();
                    }
                } else {
                    world.pos = null;
                }
            } else {
                world.pos = null;
            }
            try {
                if (info) log.info("starting the JavaScript runtime...");
                runtime = V8Host.getNodeInstance().createV8Runtime();
                JavetProxyConverter javetProxyConverter = new JavetProxyConverter();
                runtime.setConverter(javetProxyConverter);
                runtime.getGlobalObject().set("console", console.class);
                runtime.getGlobalObject().set("world", world.class);
                runtime.getExecutor(Path.of(path).toFile()).executeVoid();
            } catch (Exception e) {
                log.error(e.getMessage());
            }
        }, "JavaScript runtime").start();
    }

    @Override
    public void onInitializeClient() {
        console.log("[Command executor] Started Initialization...");
        try {
            if (FabricLoader.getInstance().getGameDir().resolve("executor/main.js").toString().equals(path) && !Path.of(path).toFile().exists()) {
                Path.of(path).toFile().getParentFile().mkdirs();
                Path.of(path).toFile().createNewFile();
                console.log("[Command executor] Created default executor file!");
            }
        } catch (Exception e) {
            console.error("Error while creating default executor file!");
        }
        createBind("start JavaScript runtime", InputUtil.Type.KEYSYM, InputUtil.GLFW_KEY_U, this::executeClient);
        createBind("Kill switch", InputUtil.Type.KEYSYM, InputUtil.GLFW_KEY_O, client -> {
            world.onTick(null);
            world.onMessage(null);
            try {runtime.close(true);} catch (Exception e) {}
        });
        console.log("[Command executor] Created keybinds Successfully");

        ClientPlayConnectionEvents.DISCONNECT.register((cleint, world_unused) -> {
            world.onTick(null);
        });
        ClientWorldEvents.AFTER_CLIENT_WORLD_CHANGE.register((client, world_unused) -> {
            world.onTick(null);
        });
    }
}

final class console {
    public static void log (String message) {
        Command_executerClient.log.info(message);
    }
    public static void warn (String message) {
        Command_executerClient.log.warn(message);
    }
    public static void error (String message) {
        Command_executerClient.log.error(message);
    }
}

final class world {
    private static V8ValueReference tick;
    private static V8ValueReference message;
    private static boolean tickEnabled = false;
    private static boolean messageEnabled = false;
    public static BlockPos pos;

    public static void tell (String message) {
        MinecraftClient.getInstance().player.networkHandler.sendChatMessage(message);
    }
    public static void whisper (String message) {
        MinecraftClient.getInstance().player.sendMessage(Text.literal(message), false);
    }
    public static void whisper (String message, Integer color) {
        MinecraftClient.getInstance().player.sendMessage(Text.literal(message).withColor(color), false);
    }
    public static void runCommand (String command) {
        MinecraftClient.getInstance().player.networkHandler.sendChatCommand(command);
    }

    public static void onTick (V8ValueFunction function) {
        if (function == null) {
            tick = null;
            return;
        }
        try {
            tick = function.toClone();
            if (tickEnabled) return;
            tickEnabled = true;
            ClientTickEvents.END_CLIENT_TICK.register(Identifier.of("command_executor_tick"), client -> {
                if (tick == null) return;
                try {
                    ((V8ValueFunction)tick).call(null);
                } catch (Exception e) {
                    console.error(e.getMessage());
                    tick = null;
                }
            });
        } catch (Exception e) {console.error(e.getMessage());}
    }

    public static void onMessage(V8ValueFunction function) {
        if (function == null) {
            message = null;
            return;
        }
        try {
            message = function.toClone();
            if (messageEnabled) return;
            messageEnabled = true;
            ClientReceiveMessageEvents.GAME.register((msg, error) -> {
                if (message == null) return;
                try {
                    ((V8ValueFunction)message).call(null, msg.getString());
                } catch (Exception e) {
                    console.error(e.getMessage());
                    message = null;
                }
            });
            ClientReceiveMessageEvents.CHAT.register((msg, SignedMessage, GameProfile, Parameters, Instant) -> {
                if (message == null) return;
                try {
                    ((V8ValueFunction)message).call(null, msg.getString());
                } catch (Exception e) {
                    console.error(e.getMessage());
                    message = null;
                }
            });
        } catch (Exception e) {console.error(e.getMessage());}
    }

    public static void executeDefault(String command) throws Exception {
        if (pos == null) {
            throw new Exception("No command block selected!");
        }
        MinecraftClient.getInstance().getNetworkHandler().sendPacket(new UpdateCommandBlockC2SPacket(pos, "", CommandBlockBlockEntity.Type.REDSTONE, false, false, false));
        MinecraftClient.getInstance().getNetworkHandler().sendPacket(new UpdateCommandBlockC2SPacket(pos, command, CommandBlockBlockEntity.Type.REDSTONE, false, false, true));
    }

    public static void execute(String command, int posX, int posY, int posZ) throws Exception {
        BlockPos newPos = new BlockPos(posX, posY, posZ);
        if (MinecraftClient.getInstance().world.getBlockEntity(newPos) == null) {
            throw new Exception("No command block selected!");
        }
        if (MinecraftClient.getInstance().world.getBlockEntity(newPos).getType() != BlockEntityType.COMMAND_BLOCK) {
            throw new Exception("No command block selected!");
        }
        MinecraftClient.getInstance().getNetworkHandler().sendPacket(new UpdateCommandBlockC2SPacket(newPos, "", CommandBlockBlockEntity.Type.REDSTONE, false, false, false));
        MinecraftClient.getInstance().getNetworkHandler().sendPacket(new UpdateCommandBlockC2SPacket(newPos, command, CommandBlockBlockEntity.Type.REDSTONE, false, false, true));
    }

    public static int getScore(String objective, String target) throws Exception {
        ScoreHolder scoreboardHolder = null;
        ScoreboardObjective scoreboardObjective = null;
        Scoreboard scoreboard = MinecraftClient.getInstance().world.getScoreboard();
        for (ScoreHolder scoreHolder : scoreboard.getKnownScoreHolders()) {
            if (scoreHolder.getNameForScoreboard().equals(target)) {
                scoreboardHolder = scoreHolder;
            }
        }
        for (ScoreboardObjective scoreObjective : scoreboard.getObjectives()) {
            if (scoreObjective.getName().equals(objective)) {
                scoreboardObjective = scoreObjective;
            }
        }
        if (scoreboardObjective == null) throw new Exception("Objective not found!");
        if (scoreboardHolder == null) throw new Exception("Target not found!");
        return scoreboard.getOrCreateScore(scoreboardHolder, scoreboardObjective).getScore();
    }

    public static List<String> getScoreboards() {
        List<String> scoreboards = new ArrayList<>();
        MinecraftClient.getInstance().world.getScoreboard().getObjectives().forEach(objective -> scoreboards.add(objective.getName()));
        return scoreboards;
    }

    public static Map<String, Integer> getScores(String objective) throws Exception {
        Scoreboard scoreboard = MinecraftClient.getInstance().world.getScoreboard();
        Map<String, Integer> scores = new HashMap<>();
        ScoreboardObjective scoreboardObjective = null;
        for (ScoreboardObjective scoreObjective : scoreboard.getObjectives()) {
            if (scoreObjective.getName().equals(objective)) {
                scoreboardObjective = scoreObjective;
            }
        }
        if (scoreboardObjective == null) throw new Exception("Objective not found!");
        for (ScoreHolder scoreHolder : scoreboard.getKnownScoreHolders()) {
            scores.put(scoreHolder.getNameForScoreboard(), scoreboard.getOrCreateScore(scoreHolder, scoreboardObjective).getScore());
        }
        return scores;
    }

    public static List<Map<String, Object>> getPlayers() {
        List<Map<String, Object>> players = new ArrayList<>();

        MinecraftClient.getInstance().world.getPlayers().forEach(player -> {
            Map<String, Object> playerData = new HashMap<>();
            playerData.put("name", player.getName().getString());
            playerData.put("uuid", player.getUuidAsString());
            playerData.put("pos", new Object[] {player.getX(), player.getY(), player.getZ()});
            playerData.put("rotation", new Object[] {player.getYaw(), player.getPitch()});
            playerData.put("health", player.getHealth());
            playerData.put("food", player.getHungerManager().getFoodLevel());
            playerData.put("motion", new Object[] {player.getVelocity().getX(), player.getVelocity().getY(), player.getVelocity().getZ()});
            playerData.put("inventory", player.getInventory());
            playerData.put("player", player);
            players.add(playerData);
        });

        return players;
    }
}
