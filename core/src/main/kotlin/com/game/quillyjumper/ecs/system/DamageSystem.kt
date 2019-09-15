package com.game.quillyjumper.ecs.system

import com.badlogic.ashley.core.Engine
import com.badlogic.ashley.core.Entity
import com.badlogic.ashley.core.EntityListener
import com.badlogic.ashley.systems.IteratingSystem
import com.game.quillyjumper.ecs.component.*
import ktx.ashley.allOf
import ktx.ashley.exclude
import ktx.ashley.get
import ktx.log.logger
import kotlin.math.max

private val LOG = logger<DamageSystem>()

class DamageSystem :
    IteratingSystem(allOf(DamageComponent::class, CollisionComponent::class).exclude(RemoveComponent::class).get()),
    EntityListener {
    override fun addedToEngine(engine: Engine) {
        engine.addEntityListener(this)
        super.addedToEngine(engine)
    }

    override fun removedFromEngine(engine: Engine) {
        engine.removeEntityListener(this)
        super.removedFromEngine(engine)
    }

    override fun entityAdded(entity: Entity) {
        // do nothing
    }

    override fun entityRemoved(entity: Entity) {
        // remove it from any damageDealt arrays in case another entity gets recreated
        // which means it would be a completely new entity but it reuses the pooled entity
        // from the array and therefore it would not get detected by the system below
        this.entities.forEach {
            it[DamageComponent.mapper]?.damagedEntities?.removeValue(entity, true)
        }
    }

    private fun isEnemy(source: Entity, collEntity: Entity): Boolean {
        source[EntityTypeComponent.mapper]?.let { type ->
            collEntity[EntityTypeComponent.mapper]?.let { collType ->
                return when (type.type) {
                    EntityType.PLAYER -> collType.type == EntityType.ENEMY
                    EntityType.ENEMY -> collType.type == EntityType.PLAYER
                    else -> false
                }
            }
        }
        return false
    }

    override fun processEntity(entity: Entity, deltaTime: Float) {
        entity[DamageComponent.mapper]?.let { damage ->
            damage.lifeSpan -= deltaTime
            if (damage.lifeSpan <= 0f) {
                entity.add(engine.createComponent(RemoveComponent::class.java))
            } else {
                entity[CollisionComponent.mapper]?.let { collision ->
                    collision.entities.forEach { collEntity ->
                        if (isEnemy(damage.source, collEntity) && !damage.damagedEntities.contains(collEntity)) {
                            collEntity[StatsComponent.mapper]?.let { stats ->
                                // entity was not damaged yet -> deal damage to it
                                // formula : dealtDamage = damage of source - armor value of enemy
                                val damageValue = max(damage.damage - stats.armor, 0f)
                                stats.life -= damageValue
                                if (stats.life <= 0f) {
                                    // entity dies because it has no more life
                                    collEntity.add(engine.createComponent(RemoveComponent::class.java))
                                }
                                // remember entities that got already damaged once to not
                                // damage them every frame
                                damage.damagedEntities.add(collEntity)

                                LOG.debug { "$damageValue damage dealt. Life left: ${stats.life}" }
                            }
                        }
                    }
                }
            }
        }
    }
}