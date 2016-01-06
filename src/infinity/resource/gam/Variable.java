// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 - 2005 Jon Olav Hauglid
// See LICENSE.txt for license information

package infinity.resource.gam;

import infinity.resource.AbstractStruct;
import infinity.resource.AbstractVariable;

public class Variable extends AbstractVariable
{
  public Variable() throws Exception
  {
    super();
  }

  public Variable(AbstractStruct superStruct, byte[] buffer, int offset, int number) throws Exception
  {
    super(superStruct, buffer, offset, number);
  }

  public Variable(AbstractStruct superStruct, String name, byte[] buffer, int offset) throws Exception
  {
    super(superStruct, name, buffer, offset);
  }
}

