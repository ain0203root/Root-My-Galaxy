import importlib.util
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MODULE = ROOT / 'core' / 'tmosd.py'
spec = importlib.util.spec_from_file_location('tmosd', MODULE)
assert spec and spec.loader
mod = importlib.util.module_from_spec(spec)
spec.loader.exec_module(mod)


def test_status_has_core_keys():
    data = mod.status()
    assert 'platform' in data
    assert 'python' in data
    assert 'services' in data


def test_service_config_parser_returns_mapping():
    sample = mod.CONF
    old = sample.read_text() if sample.exists() else None
    try:
        sample.write_text('alpha=echo ok\n# comment\n\nbeta=echo beta\n', encoding='utf-8')
        assert mod.service_commands() == {'alpha': 'echo ok', 'beta': 'echo beta'}
    finally:
        if old is None:
            sample.unlink(missing_ok=True)
        else:
            sample.write_text(old, encoding='utf-8')
