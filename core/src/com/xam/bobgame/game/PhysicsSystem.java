package com.xam.bobgame.game;

import com.badlogic.ashley.core.*;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.*;
import com.badlogic.gdx.utils.ObjectMap;
import com.esotericsoftware.minlog.Log;
import com.xam.bobgame.GameDirector;
import com.xam.bobgame.components.PhysicsBodyComponent;
import com.xam.bobgame.entity.ComponentMappers;
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

    private Vector2 tempVec = new Vector2();
    private float forceFactor = 1;

    private int velIterations = 6, posIterations = 2;
    private float simUpdateStep = SIM_UPDATE_STEP;

    public PhysicsSystem(int priority) {
        super(priority);

        listeners.put(PlayerControlEvent.class, new EventListenerAdapter<PlayerControlEvent>() {
            @Override
            public void handleEvent(PlayerControlEvent event) {
                if (enabled && event.buttonId == 0 && event.buttonState) {
                    Entity entity = getEngine().getSystem(GameDirector.class).getEntityById(event.entityId);
                    PhysicsBodyComponent pb = ComponentMappers.physicsBody.get(entity);
                    tempVec.set(event.x, event.y).sub(pb.body.getPosition()).nor().scl(500f * forceFactor);
                    pb.body.applyForceToCenter(tempVec, true);
                }
            }
        });
    }

    private void createWalls() {
        BodyDef bodyDef = new BodyDef();
        PolygonShape shape = new PolygonShape();

        bodyDef.position.set(5, 0);
        shape.setAsBox(10, 0.5f);
        walls[0] = world.createBody(bodyDef);
        walls[0].createFixture(shape, 0);

        bodyDef.position.set(0, 5);
        shape.setAsBox(0.5f, 10);
        walls[1] = world.createBody(bodyDef);
        walls[1].createFixture(shape, 0);

        bodyDef.position.set(5, 10);
        shape.setAsBox(10, 0.5f);
        walls[2] = world.createBody(bodyDef);
        walls[2].createFixture(shape, 0);

        bodyDef.position.set(10, 5);
        shape.setAsBox(0.5f, 10);
        walls[3] = world.createBody(bodyDef);
        walls[3].createFixture(shape, 0);

        shape.dispose();
    }

    @Override
    public void addedToEngine(Engine engine) {
        world = new World(new Vector2(0, 0), true);
        createWalls();

        entities = engine.getEntitiesFor(Family.all(PhysicsBodyComponent.class).get());
        engine.addEntityListener(Family.all(PhysicsBodyComponent.class).get(), new EntityListener() {
            @Override
            public void entityAdded(Entity entity) {
                PhysicsBodyComponent physicsBody = ComponentMappers.physicsBody.get(entity);
                physicsBody.body = world.createBody(physicsBody.bodyDef);
                PhysicsHistory physicsHistory = new PhysicsHistory(entity);
                physicsBody.body.setUserData(physicsHistory);
                Shape shape = physicsBody.shapeDef.createShape();
                physicsBody.fixtureDef.shape = shape;
                physicsBody.fixture = physicsBody.body.createFixture(physicsBody.fixtureDef);
//                if (!enabled) physicsBody.fixture.setSensor(true);
                shape.dispose();
            }

            @Override
            public void entityRemoved(Entity entity) {

            }
        });

        engine.getSystem(EventsSystem.class).addListeners(listeners);
    }

    @Override
    public void removedFromEngine(Engine engine) {
        entities = null;
        world.dispose();
        EventsSystem eventsSystem = engine.getSystem(EventsSystem.class);
        if (eventsSystem != null) eventsSystem.removeListeners(listeners);
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public void update(float deltaTime) {
        quantizePhysics();
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

//            Log.info("before tfm x=" + tfm.vals[0] + " y=" + tfm.vals[1] + " vel x=" + linearVel.x + " y=" + linearVel.y);

            body.setTransform(MathUtils2.quantize(tfm.vals[0], NetDriver.RES_POSITION),
                    MathUtils2.quantize(tfm.vals[1], NetDriver.RES_POSITION),
                    MathUtils2.quantize(tfm.getRotation(), NetDriver.RES_ORIENTATION));
            body.setLinearVelocity(MathUtils2.quantize(linearVel.x, NetDriver.RES_VELOCITY), MathUtils2.quantize(linearVel.y, NetDriver.RES_VELOCITY));
            body.setAngularVelocity(MathUtils2.quantize(body.getAngularVelocity(), NetDriver.RES_ORIENTATION));

//            tfm = body.getTransform();
//            linearVel = body.getLinearVelocity();
//            Log.info("after tfm x=" + tfm.vals[0] + " y=" + tfm.vals[1] + " vel x=" + linearVel.x + " y=" + linearVel.y);
            MassData md = body.getMassData();
            md.mass = MathUtils2.quantize(md.mass, NetDriver.RES_MASS);
            md.I = MathUtils2.quantize(md.I, NetDriver.RES_MASS);
            body.setMassData(md);
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

    public static class PhysicsHistory {
        public final DebugUtils.ExpoMovingAverage posXError = new DebugUtils.ExpoMovingAverage(0.1f);
        public final DebugUtils.ExpoMovingAverage posYError = new DebugUtils.ExpoMovingAverage(0.1f);

        public final Vector2 displacement = new Vector2();
        public final Entity entity;

        public PhysicsHistory(Entity entity) {
            this.entity = entity;
        }
//        public final Vector2 position = new Vector2(0, 0);
//        public final Vector2 linearVelocity = new Vector2(0, 0);
    }
}
