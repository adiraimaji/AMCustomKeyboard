package com.adiraimaji.customkeyboard;

import com.adiraimaji.customkeyboard.KeyModifier;
import com.adiraimaji.customkeyboard.KeyValue;
import org.junit.Test;
import static com.adiraimaji.customkeyboard.TestUtils.*;
import static org.junit.Assert.*;

public class KeyModifierTest
{
  public KeyModifierTest() {}

  @Test
  public void compose() throws Exception
  {
    assertEquals(eval("compose", "space", "space"), key("nbsp"));
    assertEquals(eval("compose", "-", "space"), str("~"));
    assertEquals(eval("compose", "space", "-"), str("~"));
  }
}
