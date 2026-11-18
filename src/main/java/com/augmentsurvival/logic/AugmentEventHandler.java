package com.augmentsurvival.logic;

import com.augmentsurvival.duck.DynamiteProjectileDuck;
import com.augmentsurvival.model.AugmentType;
import com.augmentsurvival.model.GoalType;
import com.augmentsurvival.model.SynergyType;
import com.augmentsurvival.network.ModPackets;
import com.augmentsurvival.state.AugmentWorldState;
import com.augmentsurvival.state.PlayerAugmentData;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ExperienceOrbEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.boss.WitherEntity;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.IronGolemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.thrown.SnowballEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.screen.CraftingScreenHandler;
import net.minecraft.screen.ScreenHandlerContext;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class AugmentEventHandler {
    private static final Map<UUID, List<ItemStack>> SOULBOUND_BACKUP = new HashMap<>();

    public static void registerAll() {
        // 1. 접속 시 상태 동기화
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity player = handler.player;
            AugmentWorldState state = AugmentWorldState.get(server);
            if (!state.isParticipant(player.getUuid()) && !state.isSpectator(player.getUuid())) {
                state.setParticipant(player.getUuid());
            }
            ModPackets.syncAllPlayers(server);
        });

        // 2. 서버 틱: 레벨업 감지, 시너지 패시브, 휴대용 화로, 그림자 망토, 골렘 친구, 목표 달성 체크
        ServerTickEvents.END_SERVER_TICK.register(AugmentEventHandler::onServerTick);

        // 3. 블록 채굴 이벤트: [1. 자동 제련], [2. 보석 상인], [광질 3스택 무작위 광물]
        PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {
            if (!(world instanceof ServerWorld serverWorld) || !(player instanceof ServerPlayerEntity serverPlayer)) return;
            AugmentWorldState worldState = AugmentWorldState.get(serverWorld.getServer());
            if (!worldState.isGameActive() || !worldState.isParticipant(serverPlayer.getUuid())) return;

            PlayerAugmentData data = worldState.getOrCreatePlayerData(serverPlayer.getUuid());
            handleOreMiningAugments(serverWorld, serverPlayer, pos, state, data);
            SynergyManager.onStoneMined(serverWorld, pos, state, data);
        });

        // 4. 아이템 우클릭: [4. 휴대용 제작·화롯대]
        UseItemCallback.REGISTER.register((player, world, hand) -> {
            ItemStack stack = player.getStackInHand(hand);
            if (AugmentItemHelper.isPortableStation(stack)) {
                if (!world.isClient && player instanceof ServerPlayerEntity serverPlayer) {
                    AugmentWorldState state = AugmentWorldState.get(serverPlayer.getServer());
                    PlayerAugmentData data = state.getOrCreatePlayerData(serverPlayer.getUuid());

                    if (serverPlayer.isSneaking()) {
                        serverPlayer.openHandledScreen(new SimpleNamedScreenHandlerFactory(
                                (syncId, inv, p) -> new PortableFurnaceScreenHandler(syncId, inv, data),
                                Text.literal("휴대용 화롯대")
                        ));
                    } else {
                        serverPlayer.openHandledScreen(new SimpleNamedScreenHandlerFactory(
                                (syncId, inv, p) -> new CraftingScreenHandler(syncId, inv, ScreenHandlerContext.create(world, serverPlayer.getBlockPos())) {
                                    @Override
                                    public boolean canUse(PlayerEntity p) {
                                        return true;
                                    }
                                },
                                Text.literal("휴대용 제작대")
                        ));
                    }
                }
                return TypedActionResult.success(stack, world.isClient());
            }
            return TypedActionResult.pass(stack);
        });

        // 5. 엔티티 타격 이벤트: [6. 그림자 망토 은신 해제] & [근접 시너지 흡혈/충격파]
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (!world.isClient && player instanceof ServerPlayerEntity serverPlayer && entity instanceof LivingEntity target) {
                AugmentWorldState state = AugmentWorldState.get(serverPlayer.getServer());
                PlayerAugmentData data = state.getOrCreatePlayerData(serverPlayer.getUuid());

                if (data.hasAugment(AugmentType.SHADOW_CLOAK) && serverPlayer.isSneaking()) {
                    data.setShadowCloakRevealUntilTick(world.getTime() + 60L); // 3초간 은신 해제
                    serverPlayer.removeStatusEffect(StatusEffects.INVISIBILITY);
                    serverPlayer.sendMessage(Text.literal("§7[그림자 망토] 공격하여 3초간 은신이 해제됩니다."), true);
                }

                int meleeSyn = data.getSynergyCount(SynergyType.MELEE);
                if (meleeSyn >= 3) {
                    serverPlayer.heal(1.0f);
                }
                if (meleeSyn >= 4 && serverPlayer.fallDistance > 0.0F && !serverPlayer.isOnGround()) {
                    Box shockBox = target.getBoundingBox().expand(3.0);
                    for (HostileEntity hostile : world.getEntitiesByClass(HostileEntity.class, shockBox, e -> e != target)) {
                        hostile.damage(serverPlayer.getDamageSources().playerAttack(serverPlayer), 4.0f);
                    }
                    ((ServerWorld) world).spawnParticles(ParticleTypes.SWEEP_ATTACK, target.getX(), target.getBodyY(0.5), target.getZ(), 5, 1.0, 0.2, 1.0, 0.0);
                }
            }
            return ActionResult.PASS;
        });

        // 6. 피해 및 사망 이벤트: [9. 불사조의 유물], [유틸 낙하 피해 경감], [보스 처치 목표], [EXP 4스택]
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (entity instanceof ServerPlayerEntity serverPlayer) {
                AugmentWorldState state = AugmentWorldState.get(serverPlayer.getServer());
                PlayerAugmentData data = state.getOrCreatePlayerData(serverPlayer.getUuid());
                int utility = data.getSynergyCount(SynergyType.UTILITY);
                if (source.isOf(DamageTypes.FALL) && utility >= 4) {
                    return false;
                }
            }
            return true;
        });

        ServerLivingEntityEvents.ALLOW_DEATH.register((entity, damageSource, damageAmount) -> {
            if (entity instanceof ServerPlayerEntity serverPlayer) {
                AugmentWorldState state = AugmentWorldState.get(serverPlayer.getServer());
                PlayerAugmentData data = state.getOrCreatePlayerData(serverPlayer.getUuid());

                // [9. 불사조의 유물] 1회 부활
                if (data.hasAugment(AugmentType.PHOENIX_RELIC) && !data.isPhoenixUsed()) {
                    data.setPhoenixUsed(true);
                    state.markDirty();

                    serverPlayer.setHealth(serverPlayer.getMaxHealth());
                    serverPlayer.clearStatusEffects();
                    serverPlayer.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 100, 1));
                    serverPlayer.addStatusEffect(new StatusEffectInstance(StatusEffects.FIRE_RESISTANCE, 100, 0));
                    serverPlayer.addStatusEffect(new StatusEffectInstance(StatusEffects.ABSORPTION, 60, 1));

                    ServerWorld sw = serverPlayer.getServerWorld();
                    sw.spawnParticles(ParticleTypes.TOTEM_OF_UNDYING, serverPlayer.getX(), serverPlayer.getBodyY(0.5), serverPlayer.getZ(), 40, 0.5, 0.8, 0.5, 0.2);
                    sw.playSound(null, serverPlayer.getBlockPos(), SoundEvents.ITEM_TOTEM_USE, SoundCategory.PLAYERS, 1.0f, 1.0f);
                    serverPlayer.sendMessage(Text.literal("§6§l[불사조의 유물] §e치명적인 피해를 막아내고 완전 부활했습니다!"), false);
                    ModPackets.syncPlayerState(serverPlayer);
                    return false;
                }

                // 사망 시 소울바운드 아이템 백업
                backupSoulboundItems(serverPlayer);
            }
            return true;
        });

        ServerLivingEntityEvents.AFTER_DEATH.register((entity, damageSource) -> {
            if (!(entity.getWorld() instanceof ServerWorld sw)) return;
            MinecraftServer server = sw.getServer();
            AugmentWorldState state = AugmentWorldState.get(server);
            if (!state.isGameActive()) return;

            // 보스 처치 목표 확인
            if (state.getCurrentGoal() == GoalType.ENDER_DRAGON && entity instanceof EnderDragonEntity) {
                state.setCurrentProgress(1);
                completeGameVictory(server, state);
            } else if (state.getCurrentGoal() == GoalType.WITHER && entity instanceof WitherEntity) {
                state.setCurrentProgress(1);
                completeGameVictory(server, state);
            }

            // EXP 4스택 추가 경험치 오브
            if (damageSource.getAttacker() instanceof ServerPlayerEntity killer) {
                PlayerAugmentData data = state.getOrCreatePlayerData(killer.getUuid());
                if (data.getSynergyCount(SynergyType.EXP) >= 4) {
                    ExperienceOrbEntity.spawn(sw, entity.getPos(), 10);
                }
            }
        });

        // 7. 리스폰 후 소울바운드 아이템([3. 가더브 곡괭이], [4. 휴대용 제작대], [5. 입체기동장치]) 복원
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            List<ItemStack> saved = SOULBOUND_BACKUP.remove(newPlayer.getUuid());
            if (saved != null) {
                for (ItemStack stack : saved) {
                    if (!newPlayer.getInventory().contains(stack)) {
                        newPlayer.getInventory().insertStack(stack);
                    }
                }
            }
            ModPackets.syncPlayerState(newPlayer);
        });
    }

    public static void backupSoulboundItems(ServerPlayerEntity player) {
        List<ItemStack> list = new ArrayList<>();
        for (int i = 0; i < player.getInventory().size(); i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (AugmentItemHelper.isSoulbound(stack)) {
                list.add(stack.copy());
            }
        }
        SOULBOUND_BACKUP.put(player.getUuid(), list);
    }

    public static void startGameSession(MinecraftServer server, GoalType goal) {
        AugmentWorldState state = AugmentWorldState.get(server);
        long now = server.getOverworld().getTime();
        state.startNewGame(goal, now);

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (state.isParticipant(player.getUuid())) {
                player.changeGameMode(GameMode.SURVIVAL);
                player.setHealth(player.getMaxHealth());
                player.getHungerManager().setFoodLevel(20);
                player.setExperienceLevel(0);
                player.setExperiencePoints(0);
                player.sendMessage(Text.literal("§a§l[증강 야생] §f게임 시작! 목표: §e" + goal.getDisplayName()), false);

                // 시작 즉시 1레벨 증강 선택 지급
                PlayerAugmentData data = state.getOrCreatePlayerData(player.getUuid());
                data.setLastProcessedLevel(1);
                data.setPendingLevelUps(1);
                data.setRerollsRemaining(1);
                data.generateChoices(false);
                ModPackets.sendOpenSelectGui(player);
            } else {
                state.setSpectator(player.getUuid());
                player.changeGameMode(GameMode.SPECTATOR);
                player.sendMessage(Text.literal("§7[증강 야생] 관전자 모드로 게임에 참여합니다."), false);
            }
        }
        ModPackets.syncAllPlayers(server);
    }

    private static void onServerTick(MinecraftServer server) {
        AugmentWorldState state = AugmentWorldState.get(server);
        if (!state.isGameActive()) return;

        boolean dirty = false;
        int totalGoalItems = 0;

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (!state.isParticipant(player.getUuid())) continue;
            PlayerAugmentData data = state.getOrCreatePlayerData(player.getUuid());

            // 레벨업 체크 (1, 3, 5, 7, 9, 10, 11 ... 40)
            int curLevel = player.experienceLevel;
            if (curLevel > data.getLastProcessedLevel()) {
                for (int lv = data.getLastProcessedLevel() + 1; lv <= curLevel; lv++) {
                    if (isAugmentGrantLevel(lv)) {
                        data.setPendingLevelUps(data.getPendingLevelUps() + 1);
                        if (data.getSynergyCount(SynergyType.EXP) >= 3) {
                            player.setHealth(player.getMaxHealth());
                            player.getHungerManager().setFoodLevel(20);
                        }
                    }
                }
                data.setLastProcessedLevel(curLevel);
                dirty = true;
            }

            // 대기 중인 레벨업 카드 생성 및 오픈
            if (data.getPendingLevelUps() > 0 && data.getPendingChoices().isEmpty()) {
                data.setPendingLevelUps(data.getPendingLevelUps() - 1);
                data.setRerollsRemaining(1);
                data.generateChoices(false);
                ModPackets.sendOpenSelectGui(player);
                dirty = true;
            }

            // 쿨타임 및 휴대용 화로 제련 틱
            data.tickCooldowns();
            data.tickPortableFurnace(player.getServerWorld());

            // 시너지 패시브 틱
            SynergyManager.applyTickPassives(player, data);

            // [6. 그림자 망토] 웅크리기 은신 + 주변 몹 어그로 해제
            if (data.hasAugment(AugmentType.SHADOW_CLOAK)) {
                long now = player.getWorld().getTime();
                if (player.isSneaking() && now >= data.getShadowCloakRevealUntilTick()) {
                    player.addStatusEffect(new StatusEffectInstance(StatusEffects.INVISIBILITY, 12, 0, true, false, false));
                    Box aggroBox = player.getBoundingBox().expand(24.0);
                    for (MobEntity mob : player.getWorld().getEntitiesByClass(MobEntity.class, aggroBox, m -> m.getTarget() == player)) {
                        mob.setTarget(null);
                        mob.setAttacker(null);
                    }
                }
            }

            // [7. 골렘 친구] AI 추적, 20칸 이탈 텔레포트, 사망 후 60초 부활
            if (data.hasAugment(AugmentType.GOLEM_FRIEND)) {
                tickGolemFriend(player, data);
            }

            // 아이템 수집 목표 카운트
            if (state.getCurrentGoal() == GoalType.DIAMOND_100) {
                totalGoalItems += player.getInventory().count(Items.DIAMOND);
            } else if (state.getCurrentGoal() == GoalType.NETHERITE_16) {
                totalGoalItems += player.getInventory().count(Items.NETHERITE_INGOT);
            }

            if (player.age % 10 == 0) {
                ModPackets.syncPlayerState(player);
            }
        }

        // 수집 목표 달성 확인
        GoalType goal = state.getCurrentGoal();
        if (goal == GoalType.DIAMOND_100 || goal == GoalType.NETHERITE_16) {
            if (totalGoalItems != state.getCurrentProgress()) {
                state.setCurrentProgress(totalGoalItems);
                dirty = true;
            }
            if (totalGoalItems >= goal.getTargetAmount()) {
                completeGameVictory(server, state);
                return;
            }
        }

        if (dirty) {
            state.markDirty();
        }
    }

    public static boolean isAugmentGrantLevel(int level) {
        if (level > 40 || level < 1) return false;
        return level == 1 || level == 3 || level == 5 || level == 7 || level >= 9;
    }

    public static void handleSelectAugment(ServerPlayerEntity player, int index) {
        MinecraftServer server = player.getServer();
        if (server == null) return;
        AugmentWorldState state = AugmentWorldState.get(server);
        PlayerAugmentData data = state.getOrCreatePlayerData(player.getUuid());

        List<AugmentType> choices = data.getPendingChoices();
        if (index < 0 || index >= choices.size()) return;

        AugmentType chosen = choices.get(index);
        choices.clear();
        data.addAugment(chosen);
        onAugmentAcquired(player, data, chosen);

        // 연쇄 레벨업 대기열이 있다면 즉시 다음 선택창 오픈
        if (data.getPendingLevelUps() > 0) {
            data.setPendingLevelUps(data.getPendingLevelUps() - 1);
            data.setRerollsRemaining(1);
            data.generateChoices(false);
            ModPackets.sendOpenSelectGui(player);
        } else {
            ModPackets.syncPlayerState(player);
        }
        state.markDirty();
    }

    public static void handleRerollAugment(ServerPlayerEntity player) {
        MinecraftServer server = player.getServer();
        if (server == null) return;
        AugmentWorldState state = AugmentWorldState.get(server);
        PlayerAugmentData data = state.getOrCreatePlayerData(player.getUuid());

        if (data.getPendingChoices().isEmpty() || data.getRerollsRemaining() <= 0) return;
        data.setRerollsRemaining(data.getRerollsRemaining() - 1);
        data.generateChoices(true); // 리롤 시 15% 확률로 카드 등급 승급
        state.markDirty();
        ModPackets.sendOpenSelectGui(player);
    }

    private static void onAugmentAcquired(ServerPlayerEntity player, PlayerAugmentData data, AugmentType chosen) {
        player.sendMessage(Text.literal("§b[증강 획득] " + chosen.getRarity().getFormattingCode() + chosen.getDisplayName()), false);
        switch (chosen) {
            case GADERVE_PICKAXE -> player.getInventory().insertStack(AugmentItemHelper.createGadervePickaxe());
            case PORTABLE_STATION -> player.getInventory().insertStack(AugmentItemHelper.createPortableStation());
            case ODM_GEAR -> player.getInventory().insertStack(AugmentItemHelper.createOdmGear());
            case GOLEM_FRIEND -> spawnAllyGolem(player, data);
            default -> {}
        }
    }

    private static void handleOreMiningAugments(ServerWorld world, ServerPlayerEntity player, BlockPos pos, BlockState state, PlayerAugmentData data) {
        Block block = state.getBlock();
        boolean autoSmelt = data.hasAugment(AugmentType.AUTO_SMELT);
        boolean gemMerchant = data.hasAugment(AugmentType.GEM_MERCHANT);
        if (!autoSmelt && !gemMerchant) return;

        int fortune = EnchantmentHelper.getLevel(Enchantments.FORTUNE, player.getMainHandStack());
        ThreadLocalRandom rand = ThreadLocalRandom.current();

        // [1. 자동 제련]: 철, 금, 구리 원석 채굴 시 즉시 주괴로 드롭
        if (autoSmelt) {
            Item smelted = null;
            if (block == Blocks.IRON_ORE || block == Blocks.DEEPSLATE_IRON_ORE) smelted = Items.IRON_INGOT;
            else if (block == Blocks.GOLD_ORE || block == Blocks.DEEPSLATE_GOLD_ORE || block == Blocks.NETHER_GOLD_ORE) smelted = Items.GOLD_INGOT;
            else if (block == Blocks.COPPER_ORE || block == Blocks.DEEPSLATE_COPPER_ORE) smelted = Items.COPPER_INGOT;

            if (smelted != null) {
                int count = (block == Blocks.COPPER_ORE || block == Blocks.DEEPSLATE_COPPER_ORE) ? rand.nextInt(2, 6) : 1;
                if (fortune > 0) count += rand.nextInt(fortune + 1);
                Block.dropStack(world, pos, new ItemStack(smelted, count));
                world.spawnParticles(ParticleTypes.FLAME, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 8, 0.25, 0.25, 0.25, 0.02);
            }
        }

        // [2. 보석 상인]: 철, 금, 다이아몬드 채굴 시 50% 확률로 1~2개 추가 드롭
        if (gemMerchant) {
            Item bonusItem = null;
            if (block == Blocks.IRON_ORE || block == Blocks.DEEPSLATE_IRON_ORE) {
                bonusItem = autoSmelt ? Items.IRON_INGOT : Items.RAW_IRON;
            } else if (block == Blocks.GOLD_ORE || block == Blocks.DEEPSLATE_GOLD_ORE) {
                bonusItem = autoSmelt ? Items.GOLD_INGOT : Items.RAW_GOLD;
            } else if (block == Blocks.DIAMOND_ORE || block == Blocks.DEEPSLATE_DIAMOND_ORE) {
                bonusItem = Items.DIAMOND;
            }

            if (bonusItem != null && rand.nextFloat() < 0.50f) {
                int extra = rand.nextInt(1, 3);
                Block.dropStack(world, pos, new ItemStack(bonusItem, extra));
                world.playSound(null, pos, SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.BLOCKS, 0.6f, 1.4f);
            }
        }
    }

    private static void spawnAllyGolem(ServerPlayerEntity owner, PlayerAugmentData data) {
        ServerWorld world = owner.getServerWorld();
        IronGolemEntity golem = EntityType.IRON_GOLEM.create(world);
        if (golem == null) return;

        golem.refreshPositionAndAngles(owner.getX() + 1.5, owner.getY(), owner.getZ() + 1.5, owner.getYaw(), 0.0f);
        golem.setPlayerCreated(true);
        golem.setCustomName(Text.literal("§e[아군] " + owner.getGameProfile().getName() + "의 골렘"));
        golem.setCustomNameVisible(true);
        world.spawnEntity(golem);

        data.setActiveGolemUuid(golem.getUuid());
        data.setGolemRespawnTimer(0);
    }

    private static void tickGolemFriend(ServerPlayerEntity owner, PlayerAugmentData data) {
        ServerWorld world = owner.getServerWorld();
        UUID golemUuid = data.getActiveGolemUuid();
        Entity entity = golemUuid != null ? world.getEntity(golemUuid) : null;

        if (!(entity instanceof IronGolemEntity golem) || !golem.isAlive()) {
            int timer = data.getGolemRespawnTimer();
            if (timer <= 0) {
                data.setGolemRespawnTimer(1200); // 사망 시 60초(1200틱) 후 부활
                owner.sendMessage(Text.literal("§e[골렘 친구] §7골렘이 쓰러졌습니다. 60초 후 부활합니다."), true);
            } else {
                timer--;
                data.setGolemRespawnTimer(timer);
                if (timer == 0) {
                    spawnAllyGolem(owner, data);
                    owner.sendMessage(Text.literal("§e[골렘 친구] §a아군 철 골렘이 부활했습니다!"), true);
                }
            }
            return;
        }

        if (owner.age % 10 != 0) return;

        // 20칸 이상 멀어지면 플레이어 곁으로 텔레포트
        if (golem.squaredDistanceTo(owner) > 400.0) {
            golem.requestTeleport(owner.getX() + 1.0, owner.getY(), owner.getZ() + 1.0);
        }

        // 주변 적대적 몹 탐색 및 공격, 플레이어 공격 금지
        if (golem.getTarget() instanceof PlayerEntity) {
            golem.setTarget(null);
        }
        if (golem.getTarget() == null || !golem.getTarget().isAlive()) {
            Box searchBox = golem.getBoundingBox().expand(16.0);
            HostileEntity nearest = world.getEntitiesByClass(HostileEntity.class, searchBox, LivingEntity::isAlive)
                    .stream()
                    .min(Comparator.comparingDouble(golem::squaredDistanceTo))
                    .orElse(null);
            if (nearest != null) {
                golem.setTarget(nearest);
            } else if (golem.squaredDistanceTo(owner) > 36.0) {
                golem.getNavigation().startMovingTo(owner, 1.2);
            }
        }
    }

    // [10. 다이너마이트 투척 (액티브 필살기)]
    public static void handleUseActiveSkill(ServerPlayerEntity player, int slotIndex) {
        MinecraftServer server = player.getServer();
        if (server == null) return;
        AugmentWorldState state = AugmentWorldState.get(server);
        if (!state.isGameActive() || !state.isParticipant(player.getUuid())) return;

        PlayerAugmentData data = state.getOrCreatePlayerData(player.getUuid());
        List<AugmentType> slots = data.getActiveSlots();
        if (slotIndex < 0 || slotIndex >= slots.size()) return;

        AugmentType skill = slots.get(slotIndex);
        int cd = data.getActiveCooldowns().getOrDefault(skill, 0);
        if (cd > 0) {
            player.sendMessage(Text.literal("§c쿨타임이 " + String.format("%.1f", cd / 20.0f) + "초 남았습니다."), true);
            return;
        }

        if (skill == AugmentType.DYNAMITE_THROW) {
            ServerWorld world = player.getServerWorld();
            SnowballEntity dynamite = new SnowballEntity(world, player);
            dynamite.setItem(new ItemStack(Items.TNT));
            dynamite.setVelocity(player, player.getPitch(), player.getYaw(), 0.0f, 1.6f, 0.5f);
            ((DynamiteProjectileDuck) dynamite).augmentsurvival$setDynamite(true);
            world.spawnEntity(dynamite);

            world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_TNT_PRIMED, SoundCategory.PLAYERS, 1.0f, 1.2f);
            data.getActiveCooldowns().put(skill, skill.getCooldownTicks());
            state.markDirty();
            ModPackets.syncPlayerState(player);
        }
    }

    public static void triggerDynamiteExplosion(ServerWorld world, Entity thrower, Vec3d pos) {
        world.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, pos.x, pos.y, pos.z, 2, 0.3, 0.3, 0.3, 0.0);
        world.spawnParticles(ParticleTypes.FLAME, pos.x, pos.y, pos.z, 30, 1.5, 1.0, 1.5, 0.1);
        world.playSound(null, BlockPos.ofFloored(pos), SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, 1.2f, 1.0f);

        // 지형 파괴 없이 적대적 몹에게만 광역 피해
        Box blastBox = new Box(pos.subtract(5.5, 5.5, 5.5), pos.add(5.5, 5.5, 5.5));
        DamageSource source = thrower instanceof LivingEntity living
                ? world.getDamageSources().explosion(thrower, living)
                : world.getDamageSources().magic();

        for (LivingEntity target : world.getEntitiesByClass(LivingEntity.class, blastBox, e -> e != thrower && !(e instanceof PlayerEntity))) {
            if (target instanceof IronGolemEntity golem && golem.isPlayerCreated()) continue;
            target.damage(source, 20.0f);
            Vec3d knockback = target.getPos().subtract(pos).normalize().multiply(1.1).add(0, 0.45, 0);
            target.addVelocity(knockback.x, knockback.y, knockback.z);
        }
    }

    private static void completeGameVictory(MinecraftServer server, AugmentWorldState state) {
        long elapsed = server.getOverworld().getTime() - state.getGameStartTick();
        state.finishGame();
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            player.sendMessage(Text.literal("§6§l==================================="), false);
            player.sendMessage(Text.literal("§e§l[증강 야생 클리어!] §f목표 달성: §a" + state.getCurrentGoal().getDisplayName()), false);
            player.sendMessage(Text.literal("§6§l==================================="), false);
            ModPackets.sendGameResult(player, elapsed);
        }
    }
}
