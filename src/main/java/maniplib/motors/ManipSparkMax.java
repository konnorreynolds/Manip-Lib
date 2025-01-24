package maniplib.motors;

import com.revrobotics.REVLibError;
import com.revrobotics.RelativeEncoder;
import com.revrobotics.spark.ClosedLoopSlot;
import com.revrobotics.spark.SparkBase.ControlType;
import com.revrobotics.spark.SparkBase.PersistMode;
import com.revrobotics.spark.SparkBase.ResetMode;
import com.revrobotics.spark.SparkClosedLoopController;
import com.revrobotics.spark.SparkLowLevel.MotorType;
import com.revrobotics.spark.SparkMax;
import com.revrobotics.spark.config.ClosedLoopConfig.FeedbackSensor;
import com.revrobotics.spark.config.SparkBaseConfig.IdleMode;
import com.revrobotics.spark.config.SparkMaxConfig;
import edu.wpi.first.math.system.plant.DCMotor;
import edu.wpi.first.wpilibj.DriverStation;
import edu.wpi.first.wpilibj.Timer;
import edu.wpi.first.wpilibj2.command.Command;
import maniplib.utils.PIDFConfig;

import java.util.function.Supplier;

import static edu.wpi.first.units.Units.Milliseconds;
import static edu.wpi.first.units.Units.Seconds;
import static edu.wpi.first.wpilibj2.command.Commands.run;

/**
 * An implementation of {@link com.revrobotics.spark.SparkMax} as a {@link ManipMotor}.
 */
public class ManipSparkMax extends ManipMotor {

    /**
     * Config retry delay.
     */
    private final double configDelay = Milliseconds.of(5).in(Seconds);
    /**
     * {@link SparkMax} Instance.
     */
    private final SparkMax motor;
    /**
     * Integrated encoder.
     */
    public RelativeEncoder encoder;
    /**
     * Closed-loop PID controller.
     */
    public SparkClosedLoopController pid;
    /**
     * Factory default already occurred.
     */
    private final boolean factoryDefaultOccurred = false;
    /**
     * Supplier for the velocity of the motor controller.
     */
    private final Supplier<Double> velocity;
    /**
     * Supplier for the position of the motor controller.
     */
    private final Supplier<Double> position;
    /**
     * Configuration object for {@link SparkMax} motor.
     */
    private final SparkMaxConfig cfg = new SparkMaxConfig();


    /**
     * Initialize the swerve motor.
     *
     * @param motor     The SwerveMotor as a SparkMax object.
     * @param motorType Motor type controlled by the {@link SparkMax} motor controller.
     */
    public ManipSparkMax(SparkMax motor, DCMotor motorType) {
        this.motor = motor;
        this.simMotor = motorType;
        factoryDefaults();
        clearStickyFaults();

        encoder = motor.getEncoder();
        pid = motor.getClosedLoopController();

        cfg.closedLoop.feedbackSensor(FeedbackSensor.kPrimaryEncoder); // Configure feedback of the PID controller as the integrated encoder.
        velocity = encoder::getVelocity;
        position = encoder::getPosition;
    }

    /**
     * Initialize the {@link ManipMotor} as a {@link SparkMax} connected to a Brushless Motor.
     *
     * @param id        CAN ID of the SparkMax.
     * @param motorType Motor type controlled by the {@link SparkMax} motor controller.
     */
    public ManipSparkMax(int id, DCMotor motorType) {
        this(new SparkMax(id, MotorType.kBrushless), motorType);
    }

    /**
     * Run the configuration until it succeeds or times out.
     *
     * @param config Lambda supplier returning the error state.
     */
    private void configureSparkMax(Supplier<REVLibError> config) {
        for (int i = 0; i < maximumRetries; i++) {
            if (config.get() == REVLibError.kOk) {
                return;
            }
            Timer.delay(configDelay);
        }
        DriverStation.reportWarning("Failure configuring motor " + motor.getDeviceId(), true);
    }

    /**
     * Get the current configuration of the {@link SparkMax}
     *
     * @return {@link SparkMaxConfig}
     */
    public SparkMaxConfig getConfig() {
        return cfg;
    }

    /**
     * Update the config for the {@link SparkMax}
     *
     * @param cfgGiven Given {@link SparkMaxConfig} which should have minimal modifications.
     */
    public void updateConfig(SparkMaxConfig cfgGiven) {
        if (!DriverStation.isDisabled()) {
            throw new RuntimeException("Configuration changes cannot be applied while the robot is enabled.");
        }
        cfg.apply(cfgGiven);
        configureSparkMax(() -> motor.configure(cfg, ResetMode.kNoResetSafeParameters, PersistMode.kPersistParameters));
    }

    /**
     * Set the voltage compensation for the swerve module motor.
     *
     * @param nominalVoltage Nominal voltage for operation to output to.
     */
    @Override
    public void setVoltageCompensation(double nominalVoltage) {
        cfg.voltageCompensation(nominalVoltage);
    }

    /**
     * Set the current limit for the swerve drive motor, remember this may cause jumping if used in conjunction with
     * voltage compensation. This is useful to protect the motor from current spikes.
     *
     * @param currentLimit Current limit in AMPS at free speed.
     */
    @Override
    public void setCurrentLimit(int currentLimit) {
        cfg.smartCurrentLimit(currentLimit);

    }

    /**
     * Set the maximum rate the open/closed loop output can change by.
     *
     * @param rampRate Time in seconds to go from 0 to full throttle.
     */
    @Override
    public void setLoopRampRate(double rampRate) {
        cfg.closedLoopRampRate(rampRate)
                .openLoopRampRate(rampRate);

    }

    /**
     * Get the motor object from the module.
     *
     * @return Motor object.
     */
    @Override
    public Object getMotor() {
        return motor;
    }

    /**
     * Get the {@link DCMotor} of the motor class.
     *
     * @return {@link DCMotor} of this type.
     */
    @Override
    public DCMotor getSimMotor() {
        if (simMotor == null) {
            simMotor = DCMotor.getNEO(1);
        }
        return simMotor;
    }

    /**
     * Configure the factory defaults.
     */
    @Override
    public void factoryDefaults() {
        // Do nothing
    }

    /**
     * Clear the sticky faults on the motor controller.
     */
    @Override
    public void clearStickyFaults() {
        configureSparkMax(motor::clearFaults);
    }

    /**
     * Configure the PIDF values for the closed loop controller.
     *
     * @param config Configuration class holding the PIDF values.
     */
    @Override
    public void configurePIDF(PIDFConfig config) {
        cfg.closedLoop.pidf(config.p, config.i, config.d, config.f)
                .iZone(config.iz)
                .outputRange(config.output.min, config.output.max);

    }

    /**
     * Configure the PID wrapping for the position closed loop controller.
     *
     * @param minInput Minimum PID input.
     * @param maxInput Maximum PID input.
     */
    @Override
    public void configurePIDWrapping(double minInput, double maxInput) {
        cfg.closedLoop
                .positionWrappingEnabled(true)
                .positionWrappingInputRange(minInput, maxInput);

    }

    /**
     * Set the idle mode.
     *
     * @param isBrakeMode Set the brake mode.
     */
    @Override
    public void setMotorBrake(boolean isBrakeMode) {
        cfg.idleMode(isBrakeMode ? IdleMode.kBrake : IdleMode.kCoast);

    }

    /**
     * Set the motor to be inverted.
     *
     * @param inverted State of inversion.
     */
    @Override
    public void setInverted(boolean inverted) {
        cfg.inverted(inverted);
    }

    /**
     * Save the configurations from flash to EEPROM.
     */
    @Override
    public void burnFlash() {
        if (!DriverStation.isDisabled()) {
            throw new RuntimeException("Config updates cannot be applied while the robot is Enabled!");
        }
        configureSparkMax(() -> {
            return motor.configure(cfg, ResetMode.kNoResetSafeParameters, PersistMode.kPersistParameters);
        });
    }

  /**
   * Sets the {@link ManipSparkMax} to follow another {@link ManipMotor}.
   *
   * @param leadMotor  lead {@link ManipMotor} to follow.
   * @param isInverted whether to invert the follower or not.
   */
  @Override
    public void setAsFollower(ManipMotor leadMotor, Boolean isInverted) {
      if (!DriverStation.isDisabled()) {
        throw new RuntimeException("Config updates cannot be applied while the robot is Enabled!");
      }
      configureSparkMax(() ->
              motor.configure(
                      cfg.follow(leadMotor.getMotorID(),
                              isInverted),
          ResetMode.kNoResetSafeParameters,
          PersistMode.kPersistParameters
      ));
  }

    /**
     * Set the percentage output.
     *
     * @param percentOutput percent out for the motor controller.
     */
    @Override
    public void set(double percentOutput) {
        motor.set(percentOutput);
    }

    /**
     * Set the closed loop PID controller reference point.
     *
     * @param setpoint    Setpoint in MPS or Angle in degrees.
     * @param feedforward Feedforward in volt-meter-per-second or kV.
     * @param controlType ControlType to run the setReference as.
     */
    @Override
    public void setReference(double setpoint, double feedforward, ControlType controlType) {
        configureSparkMax(() ->
                pid.setReference(
                        setpoint,
                        controlType,
                        ClosedLoopSlot.kSlot0,
                        feedforward));
    }

    /**
     * Set the closed loop PID controller reference point.
     *
     * @param setpoint    Setpoint in MPS or Angle in degrees.
     * @param controlType ControlType to run the setReference as.
     */
    @Override
    public void setReference(double setpoint, ControlType controlType) {
        configureSparkMax(() ->
                pid.setReference(
                        setpoint,
                        controlType,
                        ClosedLoopSlot.kSlot0));
    }

    /**
     * Set the closed loop PID controller reference point.
     *
     * @param setpoint    Setpoint in MPS or Angle in degrees.
     * @param feedforward Feedforward in volt-meter-per-second or kV.
     */
    @Override
    public void setReference(double setpoint, double feedforward) {
        configureSparkMax(() ->
                pid.setReference(
                        setpoint,
                        ControlType.kPosition,
                        ClosedLoopSlot.kSlot0,
                        feedforward));
    }

    /**
     * Set the closed loop PID controller reference point.
     *
     * @param setpoint Setpoint in MPS or Angle in degrees.
     */
    @Override
    public void setReference(double setpoint) {
        configureSparkMax(() ->
                pid.setReference(
                        setpoint,
                        ControlType.kPosition,
                        ClosedLoopSlot.kSlot0));
    }

    /**
     * Stops the motor.
     */
    @Override
    public void stopMotor() {
        motor.set(0.0);
    }

    /**
     * A command to stop the motor.
     *
     * @return a command to stop the motor.
     */
    @Override
    public Command stopMotorCommand() {
        return run(this::stopMotor);
    }

    /**
     * Get the voltage output of the motor controller.
     *
     * @return Voltage output.
     */
    @Override
    public double getVoltage() {
        return motor.getAppliedOutput() * motor.getBusVoltage();
    }

    /**
     * Set the voltage of the motor.
     *
     * @param voltage Voltage to set.
     */
    @Override
    public void setVoltage(double voltage) {
        motor.setVoltage(voltage);
    }

  @Override
  public int getMotorID() {
    return motor.getDeviceId();
  }

  /**
     * Get the applied dutycycle output.
     *
     * @return Applied dutycycle output to the motor.
     */
    @Override
    public double getAppliedOutput() {
        return motor.getAppliedOutput();
    }

    /**
     * Get the velocity of the integrated encoder.
     *
     * @return velocity
     */
    @Override
    public double getVelocity() {
        return velocity.get();
    }

    /**
     * Get the position of the integrated encoder.
     *
     * @return Position
     */
    @Override
    public double getPosition() {
        return position.get();
    }

    /**
     * Set the integrated encoder position.
     *
     * @param position Integrated encoder position.
     */
    @Override
    public void setPosition(double position) {
        configureSparkMax(() -> encoder.setPosition(position));
    }
}
