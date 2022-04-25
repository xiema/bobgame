package com.xam.bobgame.entity;

import com.badlogic.ashley.core.Engine;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.physics.box2d.BodyDef;
import com.badlogic.gdx.physics.box2d.CircleShape;
import com.badlogic.gdx.physics.box2d.FixtureDef;
import com.xam.bobgame.components.GraphicsComponent;
import com.xam.bobgame.components.IdentityComponent;
import com.xam.bobgame.components.PhysicsBodyComponent;

public class ComponentFactory {

    public static IdentityComponent identity(Engine engine, int id) {
        IdentityComponent ic = engine.createComponent(IdentityComponent.class);
        ic.id = id;
        return ic;
    }

    public static PhysicsBodyComponent physicsBody(Engine engine, BodyDef.BodyType bodyType, float xPos, float yPos,
                                                   int shapeType, float shapeVal1, float density, float friction, float restitution) {
        PhysicsBodyComponent pb = engine.createComponent(PhysicsBodyComponent.class);

        pb.bodyDef = new BodyDef();
        pb.bodyDef.type = bodyType;
        pb.bodyDef.position.set(xPos, yPos);

        CircleShape circle = new CircleShape();
        circle.setRadius(shapeVal1);

        FixtureDef fixtureDef = new FixtureDef();
        fixtureDef.shape = circle;
        fixtureDef.density = density;
        fixtureDef.friction = friction;
        fixtureDef.restitution = restitution;
        pb.fixtureDef = fixtureDef;

        circle.dispose();

        return pb;
    }

    public static GraphicsComponent graphics(Engine engine, TextureRegion txtReg, float w, float h) {
        GraphicsComponent g = engine.createComponent(GraphicsComponent.class);
        g.spriteActor.getSprite().setRegion(txtReg);
        g.spriteActor.getSprite().setSize(w, h);
        g.spriteActor.getSprite().setOriginCenter();
        return g;
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
