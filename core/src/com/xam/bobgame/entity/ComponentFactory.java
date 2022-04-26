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
import com.xam.bobgame.game.ShapeDef;
import com.xam.bobgame.graphics.TextureDef;

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

        pb.shapeDef = new ShapeDef();
        pb.shapeDef.type = ShapeDef.ShapeType.values()[shapeType];
        pb.shapeDef.shapeVal1 = shapeVal1;

        FixtureDef fixtureDef = new FixtureDef();
        fixtureDef.density = density;
        fixtureDef.friction = friction;
        fixtureDef.restitution = restitution;
        pb.fixtureDef = fixtureDef;

        return pb;
    }

    public static GraphicsComponent graphics(Engine engine, TextureDef textureDef, float w, float h) {
        GraphicsComponent g = engine.createComponent(GraphicsComponent.class);
        g.textureDef = textureDef;
        g.spriteActor.getSprite().setRegion(new TextureRegion(textureDef.createTexture()));
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
