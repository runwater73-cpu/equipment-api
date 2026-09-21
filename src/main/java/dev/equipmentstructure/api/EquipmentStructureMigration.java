package dev.equipmentstructure.api;

/**
 * 将装备从一个数据模式版本转换到下一个版本的纯函数。
 * 结果保留主体 ID，并显式设为注册目标版本（例如 {@code next.withVersion(2)}）。
 * 作者负责迁移旧接口与部件数据；回调不应写入物品、世界或注册表。
 */
@FunctionalInterface
public interface EquipmentStructureMigration {
    EquipmentStructure migrate(EquipmentStructure structure);
}
