package gg.fishon.bobber;

import org.bukkit.Particle;

public record ParticleSpec(Particle particle, int count, double spread, double speed) {
}
