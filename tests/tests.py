import unittest

class TestAngelix(unittest.TestCase):

  def test_1(self):
      self.assertEqual(1, 1)

  def test_2(self):
      self.assertTrue(True)
      self.assertFalse(False)

  def test_3(self):
      self.assertEqual(1, 2)

if __name__ == '__main__':
    unittest.main()
