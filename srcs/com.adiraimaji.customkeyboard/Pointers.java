package com.adiraimaji.customkeyboard;

import android.os.Handler;
import android.os.Message;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.NoSuchElementException;

public final class Pointers implements Handler.Callback
{
  public static final int FLAG_P_LATCHABLE = 1;
  public static final int FLAG_P_LATCHED = (1 << 1);
  public static final int FLAG_P_FAKE = (1 << 2);
  public static final int FLAG_P_DOUBLE_TAP_LOCK = (1 << 3);
  public static final int FLAG_P_LOCKED = (1 << 4);
  public static final int FLAG_P_SLIDING = (1 << 5);
  public static final int FLAG_P_CLEAR_LATCHED = (1 << 6);
  public static final int FLAG_P_CANT_LOCK = (1 << 7);

  private Handler _longpress_handler;
  private ArrayList<Pointer> _ptrs = new ArrayList<Pointer>();
  private IPointerEventHandler _handler;
  private Config _config;

  public Pointers(IPointerEventHandler h, Config c)
  {
    _longpress_handler = new Handler(this);
    _handler = h;
    _config = c;
  }

  public Modifiers getModifiers()
  {
    return getModifiers(false);
  }

  private Modifiers getModifiers(boolean skip_latched)
  {
    int n_ptrs = _ptrs.size();
    KeyValue[] mods = new KeyValue[n_ptrs];
    int n_mods = 0;
    for (int i = 0; i < n_ptrs; i++)
    {
      Pointer p = _ptrs.get(i);
      if (p.value != null
              && !(skip_latched && p.hasFlagsAny(FLAG_P_LATCHED)
              && (p.flags & FLAG_P_LOCKED) == 0))
        mods[n_mods++] = p.value;
    }
    return Modifiers.ofArray(mods, n_mods);
  }

  public void clear()
  {
    for (Pointer p : _ptrs)
      stopLongPress(p);
    _ptrs.clear();
  }

  public boolean isKeyDown(KeyboardData.Key k)
  {
    for (Pointer p : _ptrs)
      if (p.key == k)
        return true;
    return false;
  }

  public int getKeyFlags(KeyValue kv)
  {
    for (Pointer p : _ptrs)
      if (p.value != null && p.value.equals(kv))
        return p.flags;
    return -1;
  }

  void add_fake_pointer(KeyboardData.Key key, KeyValue kv, boolean locked)
  {
    int flags = pointer_flags_of_kv(kv) | FLAG_P_FAKE | FLAG_P_LATCHED;
    if (locked)
      flags |= FLAG_P_LOCKED;
    Pointer ptr = new Pointer(-1, key, kv, 0.f, 0.f, Modifiers.EMPTY, flags);
    _ptrs.add(ptr);
    _handler.onPointerFlagsChanged(false);
  }

  public void set_fake_pointer_state(KeyboardData.Key key, KeyValue kv,
                                     boolean latched, boolean lock)
  {
    Pointer ptr = getLatched(key, kv);
    if (ptr == null)
    {
      if (latched)
      {
        add_fake_pointer(key, kv, lock);
        _handler.onPointerFlagsChanged(false);
      }
    }
    else if ((ptr.flags & FLAG_P_FAKE) == 0)
    {}
    else if (lock)
    {
      removePtr(ptr);
      if (latched)
        add_fake_pointer(key, kv, lock);
      _handler.onPointerFlagsChanged(false);
    }
    else if ((ptr.flags & FLAG_P_LOCKED) != 0)
    {}
    else if (!latched)
    {
      removePtr(ptr);
      _handler.onPointerFlagsChanged(false);
    }
  }

  public void onTouchUp(int pointerId)
  {
    Pointer ptr = getPtr(pointerId);
    if (ptr == null)
      return;
    if (ptr.hasFlagsAny(FLAG_P_SLIDING))
    {
      clearLatched();
      ptr.sliding.onTouchUp(ptr);
      return;
    }
    stopLongPress(ptr);
    KeyValue ptr_value = ptr.value;
    if (ptr.gesture != null && ptr.gesture.is_in_progress())
    {
      ptr.gesture.pointer_up();
    }
    Pointer latched = getLatched(ptr);
    if (latched != null)
    {
      removePtr(ptr);
      if ((latched.flags & (FLAG_P_FAKE | FLAG_P_DOUBLE_TAP_LOCK)) == FLAG_P_DOUBLE_TAP_LOCK)
        lockPointer(latched, false);
      else
      {
        removePtr(latched);
        _handler.onPointerUp(ptr_value, ptr.modifiers);
      }
    }
    else if ((ptr.flags & FLAG_P_LATCHABLE) != 0)
    {
      if ((ptr.flags & FLAG_P_CLEAR_LATCHED) != 0)
        clearLatched();
      ptr.flags |= FLAG_P_LATCHED;
      ptr.pointerId = -1;
      _handler.onPointerFlagsChanged(false);
    }
    else
    {
      clearLatched();
      removePtr(ptr);
      _handler.onPointerUp(ptr_value, ptr.modifiers);
    }
  }

  public void onTouchCancel()
  {
    clear();
    _handler.onPointerFlagsChanged(true);
  }

  private boolean isOtherPointerDown()
  {
    for (Pointer p : _ptrs)
      if (!p.hasFlagsAny(FLAG_P_LATCHED) &&
              (p.value == null || !p.value.hasFlagsAny(KeyValue.FLAG_SPECIAL)))
        return true;
    return false;
  }

  public void onTouchDown(float x, float y, int pointerId, KeyboardData.Key key)
  {
    if (isSliding())
      return;
    Modifiers mods = getModifiers(isOtherPointerDown());

    boolean shiftPressed = mods.has(KeyValue.Modifier.SHIFT);

    KeyValue value = key.getOutputValue(0, shiftPressed);
    if (value == null)
      value = _handler.modifyKey(key.keys[0], mods);
    else if (key.keyOutputs == null || key.keyOutputs[0] == null)
      value = _handler.modifyKey(value, mods);

    Pointer ptr = make_pointer(pointerId, key, value, x, y, mods);
    _ptrs.add(ptr);
    startLongPress(ptr);
    _handler.onPointerDown(value, false);
  }

  static final int[] DIRECTION_TO_INDEX = new int[]{
          7, 2, 2, 6, 6, 4, 4, 8, 8, 3, 3, 5, 5, 1, 1, 7
  };

  static KeyValue getKeyAtDirection(KeyboardData.Key k, int direction)
  {
    return k.keys[DIRECTION_TO_INDEX[direction]];
  }

  private KeyValue getNearestKeyAtDirection(Pointer ptr, int direction)
  {
    KeyValue k;
    for (int i = 0; i > -4; i = (~i>>31) - i)
    {
      int d = (direction + i + 16) % 16;
      KeyValue raw = getKeyAtDirection(ptr.key, d);
      if (raw == null)
        continue;

      KeyValue shifted = ptr.key.getOutputValue(DIRECTION_TO_INDEX[d], ptr.modifiers.has(KeyValue.Modifier.SHIFT));

      if (ptr.modifiers.has(KeyValue.Modifier.SHIFT)
              && shifted != null
              && ptr.key.keyOutputs != null
              && ptr.key.keyOutputs[DIRECTION_TO_INDEX[d]] != null)
      {
        k = shifted;
      }
      else
      {
        k = _handler.modifyKey(raw, ptr.modifiers);
      }
      if (k != null)
      {
        if (k.getKind() == KeyValue.Kind.Slider && Math.abs(i) >= 2)
          continue;
        return k;
      }
    }
    return null;
  }

  public void onTouchMove(float x, float y, int pointerId)
  {
    Pointer ptr = getPtr(pointerId);
    if (ptr == null)
      return;
    if (ptr.hasFlagsAny(FLAG_P_SLIDING))
    {
      ptr.sliding.onTouchMove(ptr, x, y);
      return;
    }

    if (y == 0.0) y = -400;
    float dx = x - ptr.downX;
    float dy = y - ptr.downY;

    float dist = Math.abs(dx) + Math.abs(dy);
    if (dist < _config.swipe_dist_px)
    {
      if (ptr.gesture == null || !ptr.gesture.is_in_progress())
        return;
      ptr.gesture.moved_to_center();
      ptr.value = apply_gesture(ptr, ptr.gesture.get_gesture());
      ptr.flags = 0;
    }
    else
    {
      double a = Math.atan2(dy, dx) + Math.PI;
      int direction = ((int)(a * 8 / Math.PI) + 12) % 16;
      if (ptr.gesture == null)
      {
        ptr.gesture = new Gesture(direction);
        KeyValue new_value = getNearestKeyAtDirection(ptr, direction);
        if (new_value != null)
        {
          ptr.value = new_value;
          ptr.flags = pointer_flags_of_kv(new_value);
          if (new_value.getKind() == KeyValue.Kind.Slider)
            startSliding(ptr, x, y, dx, dy, new_value);
          _handler.onPointerDown(new_value, true);
        }

      }
      else if (ptr.gesture.changed_direction(direction))
      {
        if (!ptr.gesture.is_in_progress())
        {
          _handler.onPointerFlagsChanged(true);
        }
        else
        {
          ptr.value = apply_gesture(ptr, ptr.gesture.get_gesture());
          restartLongPress(ptr);
          ptr.flags = 0;
          _handler.onPointerFlagsChanged(true);
        }
      }
    }
  }

  private Pointer getPtr(int pointerId)
  {
    for (Pointer p : _ptrs)
      if (p.pointerId == pointerId)
        return p;
    return null;
  }

  private void removePtr(Pointer ptr)
  {
    _ptrs.remove(ptr);
  }

  private Pointer getLatched(Pointer target)
  {
    return getLatched(target.key, target.value);
  }

  private Pointer getLatched(KeyboardData.Key k, KeyValue v)
  {
    if (v == null)
      return null;
    for (Pointer p : _ptrs)
      if (p.key == k && p.hasFlagsAny(FLAG_P_LATCHED)
              && p.value != null && p.value.equals(v))
        return p;
    return null;
  }

  private void clearLatched()
  {
    for (int i = _ptrs.size() - 1; i >= 0; i--)
    {
      Pointer ptr = _ptrs.get(i);
      if (ptr.hasFlagsAny(FLAG_P_LATCHED) && (ptr.flags & FLAG_P_LOCKED) == 0)
        _ptrs.remove(i);
      else if ((ptr.flags & FLAG_P_LATCHABLE) != 0)
        ptr.flags &= ~FLAG_P_LATCHABLE;
    }
  }

  private void lockPointer(Pointer ptr, boolean shouldVibrate)
  {
    ptr.flags = (ptr.flags & ~FLAG_P_DOUBLE_TAP_LOCK) | FLAG_P_LOCKED;
    _handler.onPointerFlagsChanged(shouldVibrate);
  }

  boolean isSliding()
  {
    for (Pointer ptr : _ptrs)
      if (ptr.hasFlagsAny(FLAG_P_SLIDING))
        return true;
    return false;
  }

  @Override
  public boolean handleMessage(Message msg)
  {
    for (Pointer ptr : _ptrs)
    {
      if (ptr.timeoutWhat == msg.what)
      {
        handleLongPress(ptr);
        return true;
      }
    }
    return false;
  }

  private static int uniqueTimeoutWhat = 0;

  private void startLongPress(Pointer ptr)
  {
    int what = (uniqueTimeoutWhat++);
    ptr.timeoutWhat = what;
    _longpress_handler.sendEmptyMessageDelayed(what, _config.longPressTimeout);
  }

  private void stopLongPress(Pointer ptr)
  {
    _longpress_handler.removeMessages(ptr.timeoutWhat);
  }

  private void restartLongPress(Pointer ptr)
  {
    stopLongPress(ptr);
    startLongPress(ptr);
  }

  private void handleLongPress(Pointer ptr)
  {
    if ((ptr.flags & FLAG_P_LATCHABLE) != 0)
    {
      if (!ptr.hasFlagsAny(FLAG_P_CANT_LOCK))
        lockPointer(ptr, true);
      return;
    }
    if (ptr.hasFlagsAny(FLAG_P_LATCHED) || ptr.value == null)
      return;
    KeyValue kv = KeyModifier.modify_long_press(ptr.value);
    if (!kv.equals(ptr.value))
    {
      ptr.value = kv;
      _handler.onPointerDown(kv, true);
      return;
    }
    if (kv.hasFlagsAny(KeyValue.FLAG_SPECIAL))
      return;
    if (_config.keyrepeat_enabled)
    {
      _handler.onPointerHold(kv, ptr.modifiers);
      _longpress_handler.sendEmptyMessageDelayed(ptr.timeoutWhat,
              _config.longPressInterval);
    }
  }

  void startSliding(Pointer ptr, float x, float y, float dx, float dy, KeyValue kv)
  {
    int r = kv.getSliderRepeat();
    int dirx = dx < 0 ? -r : r;
    int diry = dy < 0 ? -r : r;
    stopLongPress(ptr);
    ptr.flags |= FLAG_P_SLIDING;
    ptr.sliding = new Sliding(x, y, dirx, diry, kv.getSlider());
  }

  int pointer_flags_of_kv(KeyValue kv)
  {
    int flags = 0;
    if (kv.hasFlagsAny(KeyValue.FLAG_LATCH))
    {
      if (!kv.hasFlagsAny(KeyValue.FLAG_SPECIAL))
        flags |= FLAG_P_CLEAR_LATCHED | FLAG_P_CANT_LOCK;
      flags |= FLAG_P_LATCHABLE;
    }
    if (_config.double_tap_lock_shift &&
            kv.hasFlagsAny(KeyValue.FLAG_DOUBLE_TAP_LOCK))
      flags |= FLAG_P_DOUBLE_TAP_LOCK;
    return flags;
  }

  KeyValue apply_gesture(Pointer ptr, Gesture.Name gesture)
  {
    switch (gesture)
    {
      case None:
        return ptr.value;
      case Swipe:
        return ptr.value;
      case Roundtrip:
        return
                modify_key_with_extra_modifier(
                        ptr,
                        getNearestKeyAtDirection(ptr, ptr.gesture.current_direction()),
                        KeyValue.Modifier.GESTURE);
      case Circle:
        return
                modify_key_with_extra_modifier(ptr, ptr.key.keys[0],
                        KeyValue.Modifier.GESTURE);
      case Anticircle:
        return _handler.modifyKey(ptr.key.anticircle, ptr.modifiers);
    }
    return ptr.value;
  }

  KeyValue modify_key_with_extra_modifier(Pointer ptr, KeyValue kv,
                                          KeyValue.Modifier extra_mod)
  {
    return
            _handler.modifyKey(kv,
                    ptr.modifiers.with_extra_mod(KeyValue.makeInternalModifier(extra_mod)));
  }

  Pointer make_pointer(int p, KeyboardData.Key k, KeyValue v, float x, float y,
                       Modifiers m)
  {
    int flags = (v == null) ? 0 : pointer_flags_of_kv(v);
    return new Pointer(p, k, v, x, y, m, flags);
  }

  private static final class Pointer
  {
    public int pointerId;
    public final KeyboardData.Key key;
    public Gesture gesture;
    public KeyValue value;
    public float downX;
    public float downY;
    public Modifiers modifiers;
    public int flags;
    public int timeoutWhat;
    public Sliding sliding;

    public Pointer(int p, KeyboardData.Key k, KeyValue v, float x, float y, Modifiers m, int f)
    {
      pointerId = p;
      key = k;
      gesture = null;
      value = v;
      downX = x;
      downY = y;
      modifiers = m;
      flags = f;
      timeoutWhat = -1;
      sliding = null;
    }

    public boolean hasFlagsAny(int has)
    {
      return ((flags & has) != 0);
    }
  }

  public final class Sliding
  {
    float d = 0.f;
    float speed = 0.5f;
    float last_x;
    float last_y;
    long last_move_ms = -1;
    KeyValue.Slider slider;
    int direction_x;
    int direction_y;

    public Sliding(float x, float y, int dirx, int diry, KeyValue.Slider s)
    {
      last_x = x;
      last_y = y;
      slider = s;
      direction_x = dirx;
      direction_y = diry;
    }

    static final float SPEED_SMOOTHING = 0.7f;
    static final float SPEED_MAX = 4.f;
    static final float SPEED_VERTICAL_MULT = 0.5f;
    static final float SPEED_WORD_MULT = 0.25f;

    public void onTouchMove(Pointer ptr, float x, float y)
    {
      float travelled = Math.abs(x - last_x) + Math.abs(y - last_y);
      if (last_move_ms == -1)
      {
        if (travelled < (_config.swipe_dist_px + _config.slide_step_px))
          return;
        last_move_ms = System.currentTimeMillis();
      }
      float current_speed = speed / _config.slide_step_px;
      if (slider.isVertical()) {
        d += (y - last_y) * current_speed * direction_y * SPEED_VERTICAL_MULT;
      } else {
        if (ptr.modifiers.has(KeyValue.Modifier.CTRL))
          current_speed *= SPEED_WORD_MULT;
        d += (x - last_x) * current_speed * direction_x;
      }

      update_speed(travelled, x, y);
      int d_ = (int)d;
      if (d_ != 0)
      {
        d -= d_;
        _handler.onPointerHold(KeyValue.sliderKey(slider, d_),
                ptr.modifiers);
      }
    }

    public void onTouchUp(Pointer ptr)
    {
      removePtr(ptr);
      _handler.onPointerFlagsChanged(false);
    }

    void update_speed(float travelled, float x, float y)
    {
      long now = System.currentTimeMillis();
      float instant_speed = Math.min(SPEED_MAX,
              travelled / (float)(now - last_move_ms) + 1.f);
      speed = speed + (instant_speed - speed) * SPEED_SMOOTHING;
      last_move_ms = now;
      last_x = x;
      last_y = y;
    }
  }

  public static final class Modifiers
  {
    private final KeyValue[] _mods;
    private final int _size;

    private Modifiers(KeyValue[] m, int s)
    {
      _mods = m; _size = s;
    }

    public KeyValue get(int i) { return _mods[_size - 1 - i]; }
    public int size() { return _size; }
    public boolean has(KeyValue.Modifier m)
    {
      for (int i = 0; i < _size; i++)
      {
        KeyValue kv = _mods[i];
        switch (kv.getKind())
        {
          case Modifier:
            if (kv.getModifier().equals(m))
              return true;
        }
      }
      return false;
    }

    public Modifiers with_extra_mod(KeyValue m)
    {
      KeyValue[] newmods = Arrays.copyOf(_mods, _size + 1);
      newmods[_size] = m;
      return ofArray(newmods, newmods.length);
    }

    public Iterator<KeyValue> diff(Modifiers m2)
    {
      return new ModifiersDiffIterator(this, m2);
    }

    @Override
    public int hashCode() { return Arrays.hashCode(_mods); }
    @Override
    public boolean equals(Object obj)
    {
      return Arrays.equals(_mods, ((Modifiers)obj)._mods);
    }

    public static final Modifiers EMPTY =
            new Modifiers(new KeyValue[0], 0);

    protected static Modifiers ofArray(KeyValue[] mods, int size)
    {
      if (size > 1)
      {
        Arrays.sort(mods, 0, size);
        int j = 0;
        for (int i = 0; i < size; i++)
        {
          KeyValue m = mods[i];
          if (m != null && (i + 1 >= size || m != mods[i + 1]))
          {
            mods[j] = m;
            j++;
          }
        }
        size = j;
      }
      return new Modifiers(mods, size);
    }

    static final class ModifiersDiffIterator
            implements Iterator<KeyValue>
    {
      Modifiers m1;
      int i1 = 0;
      Modifiers m2;
      int i2 = 0;

      public ModifiersDiffIterator(Modifiers m1_, Modifiers m2_)
      {
        m1 = m1_;
        m2 = m2_;
        advance();
      }

      public boolean hasNext()
      {
        return i1 < m1._size;
      }

      public KeyValue next()
      {
        if (i1 >= m1._size)
          throw new NoSuchElementException();
        KeyValue m = m1._mods[i1];
        i1++;
        advance();
        return m;
      }

      void advance()
      {
        while (i1 < m1.size())
        {
          KeyValue m = m1._mods[i1];
          while (true)
          {
            if (i2 >= m2._size)
              return;
            int d = m.compareTo(m2._mods[i2]);
            if (d < 0)
              return;
            i2++;
            if (d == 0)
              break;
          }
          i1++;
        }
      }
    }
  }

  public interface IPointerEventHandler
  {
    public KeyValue modifyKey(KeyValue k, Modifiers mods);
    public void onPointerDown(KeyValue k, boolean isSwipe);
    public void onPointerUp(KeyValue k, Modifiers mods);
    public void onPointerFlagsChanged(boolean shouldVibrate);
    public void onPointerHold(KeyValue k, Modifiers mods);
  }
}