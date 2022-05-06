package com.xam.bobgame.entity;

import com.badlogic.ashley.core.Engine;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.physics.box2d.BodyDef;
import com.badlogic.gdx.physics.box2d.FixtureDef;
import com.xam.bobgame.components.*;
import com.xam.bobgame.game.ShapeDef;
import com.xam.bobgame.graphics.TextureDef;

public class ComponentFactory {

    public static IdentityComponent identity(Engine engine, int id, EntityType type) {
        IdentityComponent ic = engine.createComponent(IdentityComponent.class);
        ic.id = id;
        ic.type = type;
        return ic;
    }

    public static PhysicsBodyComponent physicsBody(Engine engine, BodyDef.BodyType bodyType, float xPos, float yPos, float linearDamping,
                                                   int shapeType, float shapeVal1, float density, float friction, float restitution, boolean isSensor) {
        PhysicsBodyComponent pb = engine.createComponent(PhysicsBodyComponent.class);

        pb.bodyDef = new BodyDef();
        pb.bodyDef.type = bodyType;
        pb.bodyDef.position.set(xPos, yPos);
        pb.bodyDef.linearDamping = linearDamping;

        pb.shapeDef = new ShapeDef();
        pb.shapeDef.type = ShapeDef.ShapeType.values()[shapeType];
        pb.shapeDef.shapeVal1 = shapeVal1;

        FixtureDef fixtureDef = new FixtureDef();
        fixtureDef.density = density;
        fixtureDef.friction = friction;
        fixtureDef.restitution = restitution;
        fixtureDef.isSensor = isSensor;
        pb.fixtureDef = fixtureDef;

        return pb;
    }

    public static GraphicsComponent graphics(Engine engine, TextureDef textureDef, float w, float h, int z) {
        GraphicsComponent g = engine.createComponent(GraphicsComponent.class);
        g.textureDef = textureDef;
        g.spriteActor.getSprite().setSize(w, h);
        g.spriteActor.getSprite().setOriginCenter();
        g.z = z;
        return g;
    }

    public static HazardComponent hazard(Engine engine) {
        HazardComponent h = engine.createComponent(HazardComponent.class);
        return h;
    }

    public static GravitationalFieldComponent gravField(Engine engine, float strength, float radius) {
        GravitationalFieldComponent gf = engine.createComponent(GravitationalFieldComponent.class);
        gf.strength = strength;
        gf.radius = radius;
        return gf;
    }

    public static PickupComponent pickup(Engine engine, float maxLifeTime) {
        PickupComponent p = engine.createComponent(PickupComponent.class);
        p.maxLifeTime = maxLifeTime;
        return p;
    }

    public static BuffableComponent buffable(Engine engine) {
        BuffableComponent b = engine.createComponent(BuffableComponent.class);
        return b;
    }

    public static Texture textureCircle(int wh, int radius, Color color) {
        Pixmap pmap = new Pixmap(wh, wh, Pixmap.Format.RGBA8888);
        pmap.setColor(color);
        pmap.fillCircle(wh / 2, wh / 2, radius);
        Texture tx = new Texture(pmap);
        pmap.dispose();
        return tx;
    }
}
