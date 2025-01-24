package bigeror.command_executer.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.block.entity.CommandBlockBlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.network.packet.c2s.play.UpdateCommandBlockC2SPacket;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Command_executerClient implements ClientModInitializer {
  private static final Logger log = LoggerFactory.getLogger("command-executer");
  private static final Log logger = (s) -> log.info("[Command executor] " + s);

  static {
    System.setProperty("java.awt.headless", "false");
  }

  private final ExecutorService executorService = Executors.newSingleThreadExecutor();
  private final SimpleConfig CONFIG = SimpleConfig.of("command_executer").provider(this::provider).request();
  private final String IP = CONFIG.getOrDefault("IP", "localhost");
  private final int PORT = CONFIG.getOrDefault("PORT", 8000);
  private final boolean command_block_executer = CONFIG.getOrDefault("Enable_command_blocks", false);
  private boolean executer_state = false;
  private Socket server = new Socket();
  private BufferedReader reader = null;
  private CompletableFuture<?> readBuffer = null;
  private PrintWriter printwriter = null;

  private void webSocketConnection(MinecraftClient client) throws IOException {
    server = new Socket(IP, PORT);
    printwriter = new PrintWriter(server.getOutputStream());

    executer_state = true;
    client.player.sendMessage(Text.literal("command executer had been enabled"), false);
    client.player.sendMessage(Text.literal("app is listening on IP: " + IP + ", port: " + PORT), false);

    InputStreamReader in = new InputStreamReader(server.getInputStream());
    reader = new BufferedReader(in);

    readBuffer = CompletableFuture.runAsync(() -> {
      String line = null;
      try {
        while ((line = reader.readLine()) != null) {
          if (!executer_state) return;
          client.player.networkHandler.sendChatCommand(line);
          logger.info(line);
        }
      } catch (IOException e) {
        log.error(e.toString());
      }
    }, executorService);
  }

  private void commandBlockWebSocketConnection(MinecraftClient client) throws IOException {

    Vec3d hitPos = client.crosshairTarget.getPos();
    BlockPos blockPos = new BlockPos((int) Math.ceil(hitPos.getX()) - 1, (int) Math.ceil(hitPos.getY()) - 1, (int) Math.ceil(hitPos.getZ()) - 1);
    try {
      if (!(client.world.getBlockEntity(blockPos).getType() == BlockEntityType.COMMAND_BLOCK)) {
        client.player.sendMessage(Text.literal("You're looking at wrong block. You should look at command block to activate command executor."), false);
        return;
      }
    } catch (Exception e) {
      client.player.sendMessage(Text.literal("You're looking at wrong block. You should look at command block to activate command executor."), false);
      return;
    }

    server = new Socket(IP, PORT);
    printwriter = new PrintWriter(server.getOutputStream());

    executer_state = true;
    client.player.sendMessage(Text.literal("command block executer had been enabled"), false);
    client.player.sendMessage(Text.literal("app is listening on IP: " + IP + ", port: " + PORT), false);

    InputStreamReader in = new InputStreamReader(server.getInputStream());
    reader = new BufferedReader(in);

    readBuffer = CompletableFuture.runAsync(() -> {
      String line = null;
      try {
        while ((line = reader.readLine()) != null) {
          if (!executer_state) return;
          if (line != null && line != "") {
            client.getNetworkHandler().sendPacket(new UpdateCommandBlockC2SPacket(blockPos, "", CommandBlockBlockEntity.Type.REDSTONE, false, false, false));
            client.getNetworkHandler().sendPacket(new UpdateCommandBlockC2SPacket(blockPos, line, CommandBlockBlockEntity.Type.REDSTONE, false, false, true));
            logger.info(line);
          }
        }
      } catch (IOException e) {
        log.error(e.toString());
      }
    }, executorService);
  }

  private String provider(String filename) {
    return "# Config file for command executer made by bigeror\n" + "IP=localhost\n" + "PORT=8000\n" + "Enable_command_blocks=false";
  }

  private void createBind(String name, InputUtil.Type type, Integer key, executable function) {
    KeyBinding keyBind = KeyBindingHelper.registerKeyBinding(new KeyBinding(name, type, key, "command executor"));
    ClientTickEvents.END_CLIENT_TICK.register(client -> {
      while (keyBind.wasPressed()) {
        function.executeClient(client);
      }
    });
  }

  @Override
  public void onInitializeClient() {
    // keybinds code
    createBind("toggle connection", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_R, (client) -> {
      if (executer_state) {
        executer_state = false;
        readBuffer.cancel(true);
        try {
          server.close();
        } catch (IOException e) {
          log.error(e.toString());
        }
        client.player.sendMessage(Text.literal("command executer had been disabled"), false);
      } else {
        try {
          webSocketConnection(client);
        } catch (Exception e) {
          log.error(e.toString());
          client.player.sendMessage(Text.literal(e.toString()), false);
        }
      }
    });
    if (command_block_executer) createBind("test", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_Y, (client) -> {
      if (executer_state) {
        executer_state = false;
        readBuffer.cancel(true);
        try {
          server.close();
        } catch (IOException e) {
          log.error(e.toString());
        }
        client.player.sendMessage(Text.literal("command executer had been disabled"), false);
      } else {
        try {
          commandBlockWebSocketConnection(client);
        } catch (Exception e) {
          log.error(e.toString());
          client.player.sendMessage(Text.literal(e.toString()), false);
        }
      }
    });

    // events code
    ClientReceiveMessageEvents.GAME.register((message, error) -> {
      if (!executer_state) return;
      printwriter.println(message.toString());
      printwriter.flush();
    });
    ClientReceiveMessageEvents.CHAT.register((message, SignedMessage, GameProfile, Parameters, Instant) -> {
      if (!executer_state) return;
      if (SignedMessage == null) return;
      printwriter.println(SignedMessage.toString());
      printwriter.flush();
      printwriter.println(SignedMessage.getContent().getLiteralString());
      printwriter.flush();
    });
    ClientLifecycleEvents.CLIENT_STOPPING.register((client) -> {
      if (!executer_state) return;
      readBuffer.cancel(true);
      try {
        server.close();
      } catch (IOException e) {
        log.error(e.toString());
      }
      client.player.sendMessage(Text.literal("command executer had been disabled"), false);
    });

    logger.info("mod loaded");
  }

  private interface executable {
    void executeClient(MinecraftClient client);
  }

  private interface Log {
    void info(String s);
  }
}

