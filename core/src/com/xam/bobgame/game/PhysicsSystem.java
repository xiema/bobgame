package com.xam.bobgame.game;

import com.badlogic.ashley.core.*;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.*;
import com.badlogic.gdx.utils.ObjectMap;
import com.badlogic.gdx.utils.Pools;
import com.esotericsoftware.minlog.Log;
import com.xam.bobgame.GameDirector;
import com.xam.bobgame.GameEngine;
import com.xam.bobgame.GameProperties;
import com.xam.bobgame.components.HazardComponent;
import com.xam.bobgame.components.PhysicsBodyComponent;
import com.xam.bobgame.entity.ComponentMappers;
import com.xam.bobgame.entity.EntityUtils;
import com.xam.bobgame.events.*;
import com.xam.bobgame.net.NetDriver;
import com.xam.bobgame.utils.DebugUtils;
import com.xam.bobgame.utils.MathUtils2;

public class PhysicsSystem extends EntitySystem {
    public static final float SIM_UPDATE_STEP = 1f / 60f;

    private ImmutableArray<Entity> entities;

    private World world;
    private Body[] walls = new Body[4];
    private boolean enabled = false;

    private ObjectMap<Class<? extends GameEvent>, GameEventListener> listeners = new ObjectMap<>();
    private ObjectMap<Family, EntityListener> entityListeners = new ObjectMap<>();

    private Vector2 tempVec = new Vector2();
    private Vector2 tempVec2 = new Vector2();
    private Vector2 tempVec3 = new Vector2();
    private float forceFactor = 1;

    private int velIterations = 6, posIterations = 2;
    private float simUpdateStep = SIM_UPDATE_STEP;

    public PhysicsSystem(int priority) {
        super(priority);

        listeners.put(ButtonReleaseEvent.class, new EventListenerAdapter<ButtonReleaseEvent>() {
            @Override
            public void handleEvent(ButtonReleaseEvent event) {
                if (enabled) {
                    Entity entity = getEngine().getSystem(GameDirector.class).getPlayerEntity(event.playerId);
                    if (entity == null) return;
                    PhysicsBodyComponent pb = ComponentMappers.physicsBody.get(entity);
//                    tempVec.set(event.x, event.y).sub(pb.body.getPosition()).nor().scl(500f * forceFactor);
                    float strength = GameProperties.PLAYER_FORCE_STRENGTH * MathUtils2.mirror.apply(event.holdDuration / GameProperties.CHARGE_DURATION_2);
                    tempVec.set(event.x, event.y).sub(pb.body.getPosition()).nor();
                    tempVec2.set(pb.body.getLinearVelocity()).scl(pb.body.getMass() / SIM_UPDATE_STEP);
                    float scalarProj = tempVec2.dot(tempVec);
                    tempVec3.set(tempVec).scl(-scalarProj).add(tempVec2);
                    pb.body.applyForceToCenter(tempVec.scl(Math.max(0, strength - tempVec3.len())).sub(tempVec3), true);
                }
            }
        });
        listeners.put(PlayerBallSpawnedEvent.class, new EventListenerAdapter<PlayerBallSpawnedEvent>() {
            @Override
            public void handleEvent(PlayerBallSpawnedEvent event) {
                Entity entity = ((GameEngine) getEngine()).getEntityById(event.entityId);
                if (entity == null) return;
                PhysicsBodyComponent pb = ComponentMappers.physicsBody.get(entity);
                pb.body.applyForceToCenter(MathUtils.random() * 500f, MathUtils.random() * 500f, true);
            }
        });

        entityListeners.put(Family.all(PhysicsBodyComponent.class).get(), new EntityListener() {
            @Override
            public void entityAdded(Entity entity) {
                PhysicsBodyComponent physicsBody = ComponentMappers.physicsBody.get(entity);
                physicsBody.body = world.createBody(physicsBody.bodyDef);
                PhysicsHistory physicsHistory = new PhysicsHistory(entity);
                physicsBody.body.setUserData(physicsHistory);
                Shape shape = physicsBody.shapeDef.createShape();
                physicsBody.fixtureDef.shape = shape;
                physicsBody.fixture = physicsBody.body.createFixture(physicsBody.fixtureDef);
                physicsBody.body.setFixedRotation(true);
//                if (!enabled) physicsBody.fixture.setSensor(true);
                shape.dispose();
            }

            @Override
            public void entityRemoved(Entity entity) {
                PhysicsBodyComponent physicsBody = ComponentMappers.physicsBody.get(entity);
                if (physicsBody.body == null) return;
                if (physicsBody.fixture != null) physicsBody.body.destroyFixture(physicsBody.fixture);
                world.destroyBody(physicsBody.body);
            }
        });
    }

    private void createWalls() {
        BodyDef bodyDef = new BodyDef();
        PolygonShape shape = new PolygonShape();

        // bottom
        bodyDef.position.set(GameProperties.MAP_WIDTH * 0.5f, 0);
        shape.setAsBox(GameProperties.MAP_WIDTH, 0.5f);
        walls[0] = world.createBody(bodyDef);
        walls[0].createFixture(shape, 0);

        // left
        bodyDef.position.set(0, GameProperties.MAP_HEIGHT * 0.5f);
        shape.setAsBox(0.5f, GameProperties.MAP_HEIGHT);
        walls[1] = world.createBody(bodyDef);
        walls[1].createFixture(shape, 0);

        // top
        bodyDef.position.set(GameProperties.MAP_WIDTH * 0.5f, GameProperties.MAP_HEIGHT);
        shape.setAsBox(GameProperties.MAP_WIDTH, 0.5f);
        walls[2] = world.createBody(bodyDef);
        walls[2].createFixture(shape, 0);

        // right
        bodyDef.position.set(GameProperties.MAP_WIDTH, GameProperties.MAP_HEIGHT * 0.5f);
        shape.setAsBox(0.5f, GameProperties.MAP_HEIGHT);
        walls[3] = world.createBody(bodyDef);
        walls[3].createFixture(shape, 0);

        shape.dispose();
    }

    @Override
    public void addedToEngine(Engine engine) {
        world = new World(new Vector2(0, 0), true);
        world.setContactListener(contactListener);
        createWalls();

        entities = engine.getEntitiesFor(Family.all(PhysicsBodyComponent.class).get());
        EntityUtils.addEntityListeners(engine, entityListeners);

        engine.getSystem(EventsSystem.class).addListeners(listeners);
    }

    @Override
    public void removedFromEngine(Engine engine) {
        world.dispose();
        world = null;
        entities = null;
        EntityUtils.removeEntityListeners(engine, entityListeners);
        EventsSystem eventsSystem = engine.getSystem(EventsSystem.class);
        if (eventsSystem != null) eventsSystem.removeListeners(listeners);
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public void update(float deltaTime) {
        world.step(simUpdateStep, velIterations, posIterations);
        quantizePhysics();
    }

    public void clearForces() {
        world.clearForces();
    }

    private void quantizePhysics() {
        for (Entity entity : entities) {
            PhysicsBodyComponent physicsBody = ComponentMappers.physicsBody.get(entity);
            Body body = physicsBody.body;
            Transform tfm = body.getTransform();
            Vector2 linearVel = body.getLinearVelocity();

            float x = MathUtils2.quantize(tfm.vals[0], NetDriver.RES_POSITION);
            float y = MathUtils2.quantize(tfm.vals[1], NetDriver.RES_POSITION);
            body.setTransform(x, y, MathUtils2.quantize(tfm.getRotation(), NetDriver.RES_ORIENTATION));
            body.setLinearVelocity(MathUtils2.quantize(linearVel.x, NetDriver.RES_VELOCITY), MathUtils2.quantize(linearVel.y, NetDriver.RES_VELOCITY));
            body.setAngularVelocity(MathUtils2.quantize(body.getAngularVelocity(), NetDriver.RES_ORIENTATION));

            MassData md = body.getMassData();
            md.mass = MathUtils2.quantize(md.mass, NetDriver.RES_MASS);
            md.I = MathUtils2.quantize(md.I, NetDriver.RES_MASS);
            body.setMassData(md);

            tempVec.set(x, y).sub(physicsBody.prevPos);
            physicsBody.xJitterCount = tempVec.x * physicsBody.displacement.x < 0 ? Math.min(3, physicsBody.xJitterCount + 1) : Math.max(0, physicsBody.xJitterCount - 1);
            physicsBody.yJitterCount = tempVec.y * physicsBody.displacement.y < 0 ? Math.min(3, physicsBody.yJitterCount + 1) : Math.max(0, physicsBody.yJitterCount - 1);
            physicsBody.prevPos.set(x, y);
            physicsBody.displacement.set(tempVec);
        }
    }

    public void setForceFactor(float forceFactor) {
        this.forceFactor = forceFactor;
    }

    public float getForceFactor() {
        return forceFactor;
    }

    public void setVelIterations(int velIterations) {
        this.velIterations = velIterations;
    }

    public void setPosIterations(int posIterations) {
        this.posIterations = posIterations;
    }

    public int getVelIterations() {
        return velIterations;
    }

    public int getPosIterations() {
        return posIterations;
    }

    public void setSimUpdateStep(float simUpdateStep) {
        this.simUpdateStep = simUpdateStep;
    }

    public float getSimUpdateStep() {
        return simUpdateStep;
    }

    private Entity queryEntity = null;

    public Entity queryAABB(float x1, float y1, float x2, float y2) {
        queryEntity = null;
        world.QueryAABB(entityQueryCallback, x1, y1, x2, y2);
        return queryEntity;
    }

    private QueryCallback entityQueryCallback = new QueryCallback() {
        @Override
        public boolean reportFixture(Fixture fixture) {
            queryEntity = ((PhysicsHistory) fixture.getBody().getUserData()).entity;
            return false;
        }
    };

    private ContactListener contactListener = new ContactListener() {
        @Override
        public void beginContact(Contact contact) {
            Entity entity1 = getEntity(contact.getFixtureA());
            Entity entity2 = getEntity(contact.getFixtureB());
            if (entity1 == null || entity2 == null) return;
            HazardComponent hazard1 = ComponentMappers.hazards.get(entity1);
            HazardComponent hazard2 = ComponentMappers.hazards.get(entity2);
            HazardContactEvent event = null;
            if (hazard1 != null && hazard2 == null) {
                event = Pools.obtain(HazardContactEvent.class);
                event.entity = entity2;
                event.hazard = entity1;
            }
            else if (hazard2 != null && hazard1 == null) {
                event = Pools.obtain(HazardContactEvent.class);
                event.entity = entity1;
                event.hazard = entity2;
            }
            if (event != null) {
                getEngine().getSystem(EventsSystem.class).queueEvent(event);
            }
        }

        @Override
        public void endContact(Contact contact) {

        }

        @Override
        public void preSolve(Contact contact, Manifold oldManifold) {

        }

        @Override
        public void postSolve(Contact contact, ContactImpulse impulse) {

        }
    };

    private static Entity getEntity(Fixture fixture) {
        return getEntity(fixture.getBody());
    }

    private static Entity getEntity(Body body) {
        Object userData = body.getUserData();
        return userData == null ? null : ((PhysicsHistory) userData).entity;
    }

    public static class PhysicsHistory {
        public final DebugUtils.ExpoMovingAverage posXError = new DebugUtils.ExpoMovingAverage(0.1f);
        public final DebugUtils.ExpoMovingAverage posYError = new DebugUtils.ExpoMovingAverage(0.1f);

        public final Entity entity;

        public PhysicsHistory(Entity entity) {
            this.entity = entity;
        }
    }
}
