package club.somc.whitelist;

import club.somc.protos.MinecraftAccountWhitelistStateUpdated;
import club.somc.protos.MinecraftWhitelistState;
import club.somc.protos.UnwhitelistMinecraftAccount;
import club.somc.protos.WhitelistMinecraftAccount;
import com.google.protobuf.InvalidProtocolBufferException;
import io.nats.client.Connection;
import io.nats.client.Dispatcher;
import io.nats.client.Nats;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.math.BigInteger;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeoutException;

public class WhitelistPlugin extends JavaPlugin {

    Connection nc;

    @Override
    public void onEnable() {
        super.onEnable();
        this.saveDefaultConfig();

        try {
            this.nc = Nats.connect(getConfig().getString("natsUrl"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        Dispatcher dispatcher = nc.createDispatcher((msg) -> {
            Bukkit.getScheduler().runTask(this, () -> {
                try {
                    if (msg.getSubject().equals("minecraft.whitelist.add")) {
                        WhitelistMinecraftAccount event = null;
                        event = WhitelistMinecraftAccount.parseFrom(msg.getData());

                        // UUID.fromString(event.getUuid())
                        String uuidStr = event.getUuid().replace("-", "");
                        UUID uuid = new UUID(
                                new BigInteger(uuidStr.substring(0, 16), 16).longValue(),
                                new BigInteger(uuidStr.substring(16), 16).longValue()
                        );
                        OfflinePlayer player = Bukkit.getOfflinePlayer(uuid);
                        player.setWhitelisted(true);

                        MinecraftAccountWhitelistStateUpdated res = MinecraftAccountWhitelistStateUpdated.newBuilder()
                                .setUuid(event.getUuid())
                                .setState(MinecraftWhitelistState.WHITELISTED)
                                .build();

                        nc.publish(msg.getReplyTo(), res.toByteArray());
                        nc.publish("minecraft.whitelist.change", res.toByteArray());
                    }

                    if (msg.getSubject().equals("minecraft.whitelist.remove")) {
                        UnwhitelistMinecraftAccount event = null;
                        event = UnwhitelistMinecraftAccount.parseFrom(msg.getData());

                        // UUID.fromString(event.getUuid())
                        String uuidStr = event.getUuid().replace("-", "");
                        UUID uuid = new UUID(
                                new BigInteger(uuidStr.substring(0, 16), 16).longValue(),
                                new BigInteger(uuidStr.substring(16), 16).longValue()
                        );

                        OfflinePlayer player = Bukkit.getOfflinePlayer(uuid);
                        player.setWhitelisted(false);

                        MinecraftAccountWhitelistStateUpdated res = MinecraftAccountWhitelistStateUpdated.newBuilder()
                                .setUuid(event.getUuid())
                                .setState(MinecraftWhitelistState.NOT_WHITELISTED)
                                .build();

                        nc.publish(msg.getReplyTo(), res.toByteArray());
                        nc.publish("minecraft.whitelist.change", res.toByteArray());
                    }
                } catch (InvalidProtocolBufferException e) {
                    throw new RuntimeException(e);
                }
            });
        });
        dispatcher.subscribe("minecraft.whitelist.add");
        dispatcher.subscribe("minecraft.whitelist.remove");
    }

    @Override
    public void onDisable() {
        super.onDisable();

        if (this.nc != null) {
            try {
                this.nc.drain(Duration.ofSeconds(5));
            } catch (TimeoutException e) {
                //throw new RuntimeException(e);
            } catch (InterruptedException e) {
                //throw new RuntimeException(e);
            }
        }
    }
}
