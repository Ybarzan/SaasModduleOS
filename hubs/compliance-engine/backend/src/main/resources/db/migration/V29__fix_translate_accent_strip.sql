-- Fix V24 translate() mismatch: replacement string had 23 chars for 21 source chars
UPDATE taric_rates SET search_text = replace(
  replace(
    replace(
      replace(
        translate(LOWER(description),
          chr(233)||chr(232)||chr(234)||chr(235)||chr(224)||chr(226)||chr(238)||chr(239)||chr(244)||chr(249)||chr(251)||chr(252)||chr(255)||chr(231)||chr(241)||chr(248)||chr(229)||chr(228)||chr(246)||chr(253)||chr(240),
          'eeeeaaiioouuycnaaooyd'),
        chr(339), 'oe'),
      chr(230), 'ae'),
    chr(223), 'ss'),
  chr(254), 'th');
